package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.constraints.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.CapacityNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionTemplateFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerSessionValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.TemporalAdjusters
import java.util.stream.Stream

@Service
@Transactional
class SessionService(
  private val sessionDatesUtil: SessionDatesUtil,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val prisonerService: PrisonerService,
  private val visitService: VisitService,
  @Value("\${policy.session.booking-notice-period.minimum-days:2}")
  private val policyNoticeDaysMin: Long,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
  @Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @Value("\${policy.session.non-association.filter:false}")
  private val policyFilterNonAssociation: Boolean,
  @Value("\${policy.session.non-association.whole-day:true}")
  private val policyNonAssociationWholeDay: Boolean,
  private val sessionValidator: PrisonerSessionValidator,
  private val prisonerValidationService: PrisonerValidationService,
  private val prisonConfigService: PrisonConfigService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonCode: String,
    prisonerId: String? = null,
    noticeDaysMin: Long? = null,
    noticeDaysMax: Long? = null,
  ): List<VisitSessionDto> {
    LOG.debug("Enter getVisitSessions prisonCode:$prisonCode, prisonerId : $prisonerId noticeDaysMin:$noticeDaysMin noticeDaysMax:$noticeDaysMax")

    val today = LocalDate.now()
    val requestedBookableStartDate = today.plusDays(noticeDaysMin ?: policyNoticeDaysMin)
    val requestedBookableEndDate = today.plusDays(noticeDaysMax ?: policyNoticeDaysMax)

    // ensure the prisoner - if supplied belongs to the same prison as supplied prisonCode
    prisonerId?.let {
      prisonerValidationService.validatePrisonerIsFromPrison(prisonerId, prisonCode)
    }

    val prisoner = prisonerId?.let { prisonerService.getPrisoner(prisonerId) }

    var sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesBy(
      prisonCode = prisonCode,
      rangeStartDate = requestedBookableStartDate,
      rangeEndDate = requestedBookableEndDate,
    )
    LOG.debug("getVisitSessions sessionTemplates size:${sessionTemplates.size} prisonerId : $prisonerId")

    prisoner?.let {
      sessionTemplates = filterByCategory(sessionTemplates, prisoner.category)
      sessionTemplates = filterByIncentiveLevels(sessionTemplates, prisoner.incentiveLevel)
    }

    sessionTemplates = filterSessionsTemplatesForLocation(sessionTemplates, prisonerId, prisonCode)

    var sessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, requestedBookableStartDate, requestedBookableEndDate)
    }.flatten()

    if (!prisonerId.isNullOrBlank()) {
      val noAssociationConflictSessions = getNoAssociationConflictSessions(sessions, prisonerId)
      sessions = filterPrisonerConflict(sessions, prisonerId, noAssociationConflictSessions)
      populateConflict(sessions, prisonerId, noAssociationConflictSessions)
    }

    populateBookedCount(sessions)

    return sessions.sortedWith(compareBy { it.startTimestamp })
  }

  fun filterByCategory(sessionTemplates: List<SessionTemplate>, prisonerCategory: String?): List<SessionTemplate> {
    val hasSessionsWithCategoryGroups = sessionTemplates.any { it.permittedSessionCategoryGroups.isNotEmpty() }
    if (hasSessionsWithCategoryGroups) {
      return sessionTemplates.filter { sessionTemplate ->
        sessionValidator.isSessionAvailableToPrisonerCategory(prisonerCategory, sessionTemplate)
      }
    }

    return sessionTemplates
  }

  fun filterByIncentiveLevels(
    sessionTemplates: List<SessionTemplate>,
    prisonerIncentiveLevel: IncentiveLevel?,
  ): List<SessionTemplate> {
    val hasSessionsWithIncentiveLevelGroups =
      sessionTemplates.any { it.permittedSessionIncentiveLevelGroups.isNotEmpty() }
    if (hasSessionsWithIncentiveLevelGroups) {
      return sessionTemplates.filter { sessionTemplate ->
        sessionValidator.isSessionAvailableToIncentiveLevel(prisonerIncentiveLevel, sessionTemplate)
      }
    }

    return sessionTemplates
  }

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    requestedBookableStartDate: LocalDate,
    requestedBookableEndDate: LocalDate,
  ): List<VisitSessionDto> {
    val firstBookableSessionDay =
      getFirstBookableSessionDay(requestedBookableStartDate, sessionTemplate.validFromDate, sessionTemplate.dayOfWeek)
    val lastBookableSessionDay = getLastBookableSession(requestedBookableEndDate, sessionTemplate.validToDate)
    val excludeDates = getExcludeDates(sessionTemplate.prison)

    if (firstBookableSessionDay <= lastBookableSessionDay) {
      return this.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate)
        .filter { date ->
          !excludeDates.contains(date)
        }
        .map { date ->
          VisitSessionDto(
            sessionTemplateReference = sessionTemplate.reference,
            prisonCode = sessionTemplate.prison.code,
            startTimestamp = LocalDateTime.of(date, sessionTemplate.startTime),
            openVisitCapacity = sessionTemplate.openCapacity,
            closedVisitCapacity = sessionTemplate.closedCapacity,
            endTimestamp = LocalDateTime.of(date, sessionTemplate.endTime),
            visitRoom = sessionTemplate.visitRoom,
            visitType = sessionTemplate.visitType,
          )
        }
        .toList()
    }

    return emptyList()
  }

  private fun getExcludeDates(prison: Prison): Set<LocalDate> {
    val excludeDates = mutableSetOf<LocalDate>()
    if (prison.excludeDates.isNotEmpty()) {
      prison.excludeDates.forEach { excludeDates.add(it.excludeDate) }
    }
    return excludeDates.toSet()
  }

  private fun calculateDates(
    firstBookableSessionDay: LocalDate,
    lastBookableSessionDay: LocalDate,
    sessionTemplate: SessionTemplate,
  ): Stream<LocalDate> {
    return sessionDatesUtil.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate)
  }

  private fun getFirstBookableSessionDay(
    bookablePeriodStartDate: LocalDate,
    sessionStartDate: LocalDate,
    sessionDayOfWeek: DayOfWeek,
  ): LocalDate {
    var firstBookableSessionDate = sessionStartDate
    if (bookablePeriodStartDate.isAfter(firstBookableSessionDate)) {
      firstBookableSessionDate = bookablePeriodStartDate
    }

    return adjustDateByDayOfWeek(sessionDayOfWeek, firstBookableSessionDate)
  }

  private fun getLastBookableSession(
    bookablePeriodEndDate: LocalDate,
    validToDate: LocalDate?,
  ): LocalDate {
    if (validToDate == null || bookablePeriodEndDate.isBefore(validToDate)) {
      return bookablePeriodEndDate
    }

    return validToDate
  }

  fun filterSessionsTemplatesForLocation(
    sessionTemplates: List<SessionTemplate>,
    prisonerId: String?,
    prisonCode: String,
    mustHaveLocationGroups: Boolean = false,
  ): List<SessionTemplate> {
    val hasSessionsWithLocationGroups = sessionTemplates.any { it.permittedSessionLocationGroups.isNotEmpty() }
    if (hasSessionsWithLocationGroups) {
      prisonerId?.let {
        val prisonerDetailDto = prisonerService.getPrisonerHousingLocation(prisonerId, prisonCode)
        prisonerDetailDto?.let { prisonerDetail ->
          val prisonerLevels = prisonerService.getLevelsMapForPrisoner(prisonerDetail)
          return sessionTemplates.filter { sessionTemplate ->
            val keep = sessionValidator.isSessionAvailableToPrisonerLocation(prisonerLevels, sessionTemplate)
            LOG.debug("filterSessionsTemplatesForLocation prisonerId:$prisonerId template ref ${sessionTemplate.reference} Keep:$keep")
            keep
          }
        }
        return listOf()
      }
    }

    return if (mustHaveLocationGroups) listOf() else sessionTemplates
  }

  private fun filterPrisonerConflict(
    sessions: List<VisitSessionDto>,
    prisonerId: String,
    noAssociationConflictSessions: List<VisitSessionDto>,
  ): List<VisitSessionDto> {
    return sessions.filterNot {
      (policyFilterNonAssociation && noAssociationConflictSessions.contains(it)) ||
        (policyFilterDoubleBooking && sessionHasBooking(it, prisonerId))
    }
  }

  private fun populateConflict(
    sessions: List<VisitSessionDto>,
    prisonerId: String,
    noAssociationConflictSessions: List<VisitSessionDto>,
  ) {
    sessions.forEach {
      if (!policyFilterNonAssociation && noAssociationConflictSessions.contains(it)) {
        it.sessionConflicts?.add(SessionConflict.NON_ASSOCIATION)
      }
      if (!policyFilterDoubleBooking && sessionHasBooking(it, prisonerId)) {
        it.sessionConflicts?.add(SessionConflict.DOUBLE_BOOKED)
      }
    }
  }

  private fun populateBookedCount(sessions: List<VisitSessionDto>) {
    sessions.forEach {
      val visitRestrictionStatsList: List<VisitRestrictionStats> = getVisitRestrictionStats(it)
      it.openVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.OPEN, visitRestrictionStatsList)
      it.closedVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.CLOSED, visitRestrictionStatsList)
    }
  }

  private fun getCountsByVisitRestriction(
    visitRestriction: VisitRestriction,
    visitRestrictionStatsList: List<VisitRestrictionStats>,
  ): Int {
    return visitRestrictionStatsList.stream().filter { visitRestriction == it.visitRestriction }
      .mapToInt(VisitRestrictionStats::count).sum()
  }

  private fun getNoAssociationConflictSessions(
    sessions: List<VisitSessionDto>,
    prisonerId: String,
  ): List<VisitSessionDto> {
    val offenderNonAssociationList = prisonerService.getOffenderNonAssociationList(prisonerId)
    return sessions.filter {
      sessionHasNonAssociation(it, offenderNonAssociationList)
    }
  }

  private fun sessionHasNonAssociation(
    session: VisitSessionDto,
    offenderNonAssociationList: @NotNull List<OffenderNonAssociationDetailDto>,
  ): Boolean {
    if (offenderNonAssociationList.isNotEmpty()) {
      val nonAssociationPrisonerIds =
        getNonAssociationPrisonerIds(session.startTimestamp.toLocalDate(), offenderNonAssociationList)
      val startDateTimeFilter = if (policyNonAssociationWholeDay) {
        session.startTimestamp.toLocalDate()
          .atStartOfDay()
      } else {
        session.startTimestamp
      }
      val endDateTimeFilter = if (policyNonAssociationWholeDay) {
        session.endTimestamp.toLocalDate()
          .atTime(LocalTime.MAX)
      } else {
        session.endTimestamp
      }

      // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
      // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
      return visitRepository.hasActiveVisits(
        nonAssociationPrisonerIds,
        session.prisonCode,
        startDateTimeFilter,
        endDateTimeFilter,
      )
    }

    return false
  }

  private fun getNonAssociationPrisonerIds(
    startTimestamp: LocalDate,
    @NotNull offenderNonAssociationList: List<OffenderNonAssociationDetailDto>,
  ): List<String> {
    return offenderNonAssociationList.filter { isDateWithinRange(startTimestamp, it.effectiveDate, it.expiryDate) }
      .map { it.offenderNonAssociation.offenderNo }
  }

  private fun sessionHasBooking(session: VisitSessionDto, prisonerId: String): Boolean {
    return visitRepository.hasVisits(
      prisonCode = session.prisonCode,
      prisonerId = prisonerId,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
    )
  }

  private fun getVisitRestrictionStats(session: VisitSessionDto): List<VisitRestrictionStats> {
    val restrictionReservedStats = visitRepository.getCountOfReservedSessionVisitsForOpenOrClosedRestriction(
      prisonCode = session.prisonCode,
      sessionTemplateReference = session.sessionTemplateReference,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
      expiredDateAndTime = visitService.getReservedExpiredDateAndTime(),
    )

    val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
      prisonCode = session.prisonCode,
      sessionTemplateReference = session.sessionTemplateReference,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
    )

    return restrictionReservedStats + restrictionBookedStats
  }

  private fun isDateWithinRange(sessionDate: LocalDate, startDate: LocalDate, endDate: LocalDate? = null) =
    sessionDate >= startDate && (endDate == null || sessionDate <= endDate)

  fun getSessionCapacity(
    prisonCode: String,
    sessionDate: LocalDate,
    sessionStartTime: LocalTime,
    sessionEndTime: LocalTime,
  ): SessionCapacityDto {
    val dayOfWeek = sessionDate.dayOfWeek

    var sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesForSession(
      prisonCode,
      sessionDate,
      sessionStartTime,
      sessionEndTime,
      dayOfWeek,
    )

    sessionTemplates = filterSessionsTemplatesForDate(sessionDate, sessionTemplates)

    if (sessionTemplates.isEmpty()) {
      throw CapacityNotFoundException("Session capacity not found prisonCode:$prisonCode,session Date:$sessionDate, StartTime:$sessionStartTime, EndTime:$sessionEndTime, dayOfWeek:$dayOfWeek")
    }

    return SessionCapacityDto(sessionTemplates)
  }

  private fun filterSessionsTemplatesForDate(
    date: LocalDate,
    sessionTemplates: List<SessionTemplate>,
  ): List<SessionTemplate> {
    return sessionTemplates.filter { sessionTemplate ->
      sessionDatesUtil.isBiWeeklySessionActiveForDate(date, sessionTemplate)
    }.sortedWith(
      Comparator.comparing(SessionTemplate::startTime)
        .thenComparing(SessionTemplate::endTime),
    )
  }

  fun getSessionSchedule(prisonCode: String, scheduleDate: LocalDate): List<SessionScheduleDto> {
    if (prisonConfigService.isExcludedDate(prisonCode, scheduleDate)) {
      return listOf()
    } else {
      var sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesForSession(
        prisonCode,
        scheduleDate,
        scheduleDate.dayOfWeek,
      )

      sessionTemplates = filterSessionsTemplatesForDate(scheduleDate, sessionTemplates)
      return sessionTemplates.map { sessionTemplate -> createSessionInfoDto(sessionTemplate) }.toList()
    }
  }

  private fun createSessionInfoDto(sessionTemplate: SessionTemplate): SessionScheduleDto {
    val sessionTemplateFrequency = getSessionTemplateFrequency(sessionTemplate)

    return SessionScheduleDto(
      sessionTemplateReference = sessionTemplate.reference,
      startTime = sessionTemplate.startTime,
      endTime = sessionTemplate.endTime,
      capacity = SessionCapacityDto(sessionTemplate),
      prisonerLocationGroupNames = sessionTemplate.permittedSessionLocationGroups.map { it.name }.toList(),
      prisonerCategoryGroupNames = sessionTemplate.permittedSessionCategoryGroups.map { it.name }.toList(),
      prisonerIncentiveLevelGroupNames = sessionTemplate.permittedSessionIncentiveLevelGroups.map { it.name }.toList(),
      sessionTemplateFrequency = sessionTemplateFrequency,
      sessionTemplateEndDate = sessionTemplate.validToDate,
    )
  }

  private fun getSessionTemplateFrequency(sessionTemplate: SessionTemplate): SessionTemplateFrequency {
    val firstSessionDate = adjustDateByDayOfWeek(sessionTemplate.dayOfWeek, sessionTemplate.validFromDate)

    if (isNotMoreThanAWeek(firstSessionDate, sessionTemplate)) {
      return SessionTemplateFrequency.ONE_OFF
    }
    if (sessionTemplate.biWeekly) {
      return SessionTemplateFrequency.BI_WEEKLY
    }
    return SessionTemplateFrequency.WEEKLY
  }

  private fun isNotMoreThanAWeek(
    firstSessionDate: LocalDate,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionTemplate.validToDate != null && DAYS.between(firstSessionDate, sessionTemplate.validToDate) < 7
  }

  private fun adjustDateByDayOfWeek(dayOfWeek: DayOfWeek, startDate: LocalDate): LocalDate {
    if (startDate.dayOfWeek != dayOfWeek) {
      return startDate.with(TemporalAdjusters.next(dayOfWeek))
    }
    return startDate
  }
}
