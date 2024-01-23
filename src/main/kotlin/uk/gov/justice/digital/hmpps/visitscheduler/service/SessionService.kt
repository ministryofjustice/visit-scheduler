package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.constraints.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.CapacityNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerSessionValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.stream.Stream

@Service
@Transactional
class SessionService(
  private val sessionDatesUtil: SessionDatesUtil,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val applicationRepository: ApplicationRepository,
  private val prisonerService: PrisonerService,
  private val application: ApplicationService,
  @Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @Value("\${policy.session.non-association.filter:false}")
  private val policyFilterNonAssociation: Boolean,
  @Value("\${policy.session.non-association.whole-day:true}")
  private val policyNonAssociationWholeDay: Boolean,
  private val sessionValidator: PrisonerSessionValidator,
  private val prisonerValidationService: PrisonerValidationService,
  private val prisonsService: PrisonsService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonCode: String,
    prisonerId: String,
    minOverride: Int? = null,
    maxOverride: Int? = null,
  ): List<VisitSessionDto> {
    LOG.debug("Enter getVisitSessions prisonCode:$prisonCode, prisonerId : $prisonerId ")

    // ensure the prisoner - if supplied belongs to the same prison as supplied prisonCode
    val prisoner = prisonerService.getPrisoner(prisonerId).also {
      prisonerValidationService.validatePrisonerNotNull(prisonerId, it)
      prisonerValidationService.validatePrisonerIsFromPrison(it!!, prisonCode)
    }!!

    val prison = prisonsService.findPrisonByCode(prisonCode)
    val dateRange = getDateRange(prison, minOverride, maxOverride)

    var sessionTemplates = getAllSessionTemplatesForDateRange(prisonCode, dateRange)
    sessionTemplates = sessionTemplates.filter {
      isAvailableToLocation(it, sessionTemplates, prisonerId, prisonCode)
        .and(isAvailableToCategory(it, sessionTemplates, prisoner.category))
        .and(isAvailableToIncentiveLevel(it, sessionTemplates, prisoner.incentiveLevel))
    }

    val noAssociationConflictSessions: List<VisitSessionDto>

    return sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, dateRange.fromDate, dateRange.toDate)
    }.flatten().also {
      // get the sessions affected by non associations
      noAssociationConflictSessions = getNoAssociationConflictSessions(it, prisonerId)
    }.filterNot {
      // filter out the non association and double booking sessions
      hasPrisonerConflict(it, prisonerId, noAssociationConflictSessions)
    }.also {
      // set conflict flags and booked counts
      populateConflict(it, prisonerId, noAssociationConflictSessions)
      populateBookedCount(it)
    }.sortedWith(compareBy { it.startTimestamp })
  }

  private fun getDateRange(
    prison: Prison,
    minOverride: Int? = null,
    maxOverride: Int? = null,
  ): DateRange {
    val today = LocalDate.now()

    val min = minOverride ?: prison.policyNoticeDaysMin
    val max = maxOverride ?: prison.policyNoticeDaysMax

    val requestedBookableStartDate = today.plusDays(min.toLong())
    val requestedBookableEndDate = today.plusDays(max.toLong())
    return DateRange(requestedBookableStartDate, requestedBookableEndDate)
  }

  private fun getAllSessionTemplatesForDateRange(prisonCode: String, dateRange: DateRange): List<SessionTemplate> {
    return sessionTemplateRepository.findSessionTemplateMinCapacityBy(
      prisonCode = prisonCode,
      rangeStartDate = dateRange.fromDate,
      rangeEndDate = dateRange.toDate,
    )
  }

  fun isAvailableToLocation(
    sessionTemplate: SessionTemplate,
    sessionTemplates: List<SessionTemplate>,
    prisonerId: String,
    prisonCode: String,
  ): Boolean {
    val hasSessionsWithLocationGroups = sessionTemplates.any { it.permittedSessionLocationGroups.isNotEmpty() }
    return if (hasSessionsWithLocationGroups) {
      val prisonerDetailDto = prisonerService.getPrisonerHousingLocation(prisonerId, prisonCode)
      prisonerDetailDto?.let {
        val prisonerLevels = prisonerService.getLevelsMapForPrisoner(prisonerDetailDto)
        val keep = sessionValidator.isSessionAvailableToPrisonerLocation(prisonerLevels, sessionTemplate)
        LOG.debug("filterSessionsTemplatesForLocation prisonerId:$prisonerId template ref ${sessionTemplate.reference} Keep:$keep")
        keep
      } ?: true
    } else {
      true
    }
  }

  private fun isAvailableToCategory(
    sessionTemplate: SessionTemplate,
    sessionTemplates: List<SessionTemplate>,
    prisonerCategory: String?,
  ): Boolean {
    val hasSessionsWithCategoryGroups = sessionTemplates.any { sessionTemplate.permittedSessionCategoryGroups.isNotEmpty() }
    return if (hasSessionsWithCategoryGroups) {
      sessionValidator.isSessionAvailableToPrisonerCategory(prisonerCategory, sessionTemplate)
    } else {
      true
    }
  }

  private fun isAvailableToIncentiveLevel(
    sessionTemplate: SessionTemplate,
    sessionTemplates: List<SessionTemplate>,
    prisonerIncentiveLevel: IncentiveLevel?,
  ): Boolean {
    val hasSessionsWithIncentiveLevelGroups =
      sessionTemplates.any { it.permittedSessionIncentiveLevelGroups.isNotEmpty() }
    return if (hasSessionsWithIncentiveLevelGroups) {
      sessionValidator.isSessionAvailableToIncentiveLevel(prisonerIncentiveLevel, sessionTemplate)
    } else {
      true
    }
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

  private fun hasPrisonerConflict(
    session: VisitSessionDto,
    prisonerId: String,
    noAssociationConflictSessions: List<VisitSessionDto>,
  ): Boolean {
    return (
      (policyFilterNonAssociation && noAssociationConflictSessions.contains(session)) ||
        (policyFilterDoubleBooking && sessionHasBooking(session, prisonerId))
      )
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
    val prisonerNonAssociationList = prisonerService.getPrisonerNonAssociationList(prisonerId)
    return sessions.filter {
      sessionHasNonAssociation(it, prisonerNonAssociationList)
    }
  }

  private fun sessionHasNonAssociation(
    session: VisitSessionDto,
    prisonerNonAssociationList: @NotNull List<PrisonerNonAssociationDetailDto>,
  ): Boolean {
    if (prisonerNonAssociationList.isNotEmpty()) {
      val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(prisonerNonAssociationList)
      val slotDate = session.startTimestamp.toLocalDate()

      if (policyNonAssociationWholeDay) {
        return visitRepository.hasActiveVisits(
          nonAssociationPrisonerIds,
          session.prisonCode,
          slotDate,
        )
      }

      val slotTime = session.startTimestamp.toLocalTime()
      val slotEndTime = session.endTimestamp.toLocalTime()

      return visitRepository.hasActiveVisits(
        nonAssociationPrisonerIds,
        session.prisonCode,
        slotDate,
        slotTime,
        slotEndTime,
      )
    }

    return false
  }

  private fun getNonAssociationPrisonerIds(
    @NotNull prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
  ): List<String> {
    return prisonerNonAssociationList.map { it.otherPrisonerDetails.prisonerNumber }
  }

  private fun sessionHasBooking(session: VisitSessionDto, prisonerId: String): Boolean {
    if (visitRepository.hasVisits(
        prisonerId = prisonerId,
        sessionTemplateReference = session.sessionTemplateReference,
        slotDate = session.startTimestamp.toLocalDate(),
      )
    ) {
      return true
    }

    return applicationRepository.hasReservations(
      prisonerId = prisonerId,
      sessionTemplateReference = session.sessionTemplateReference,
      slotDate = session.startTimestamp.toLocalDate(),
    )
  }

  private fun getVisitRestrictionStats(session: VisitSessionDto): List<VisitRestrictionStats> {
    val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
      sessionTemplateReference = session.sessionTemplateReference,
      slotDate = session.startTimestamp.toLocalDate(),
    )

    val restrictionReservedStats = applicationRepository.getCountOfReservedSessionForOpenOrClosedRestriction(
      sessionTemplateReference = session.sessionTemplateReference,
      slotDate = session.startTimestamp.toLocalDate(),
      expiredDateAndTime = application.getExpiredApplicationDateAndTime(),
    )

    return restrictionReservedStats + restrictionBookedStats
  }

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
      sessionDatesUtil.isActiveForDate(date, sessionTemplate)
    }.sortedWith(
      Comparator.comparing(SessionTemplate::startTime)
        .thenComparing(SessionTemplate::endTime),
    )
  }

  fun getSessionSchedule(prisonCode: String, scheduleDate: LocalDate): List<SessionScheduleDto> {
    return if (prisonsService.isExcludedDate(prisonCode, scheduleDate)) {
      listOf()
    } else {
      var sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesForSession(
        prisonCode,
        scheduleDate,
        scheduleDate.dayOfWeek,
      )

      sessionTemplates = filterSessionsTemplatesForDate(scheduleDate, sessionTemplates)
      sessionTemplates.map { sessionTemplate -> createSessionScheduleDto(sessionTemplate) }.toList()
    }
  }

  private fun createSessionScheduleDto(sessionTemplate: SessionTemplate): SessionScheduleDto {
    return SessionScheduleDto(
      sessionTemplateReference = sessionTemplate.reference,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime, endTime = sessionTemplate.endTime),
      capacity = SessionCapacityDto(sessionTemplate),
      prisonerLocationGroupNames = sessionTemplate.permittedSessionLocationGroups.map { it.name }.toList(),
      prisonerCategoryGroupNames = sessionTemplate.permittedSessionCategoryGroups.map { it.name }.toList(),
      prisonerIncentiveLevelGroupNames = sessionTemplate.permittedSessionIncentiveLevelGroups.map { it.name }.toList(),
      weeklyFrequency = sessionTemplate.weeklyFrequency,
      visitType = sessionTemplate.visitType,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate, validToDate = sessionTemplate.validToDate),
    )
  }

  private fun adjustDateByDayOfWeek(dayOfWeek: DayOfWeek, startDate: LocalDate): LocalDate {
    if (startDate.dayOfWeek != dayOfWeek) {
      return startDate.with(TemporalAdjusters.next(dayOfWeek))
    }
    return startDate
  }
}

data class DateRange(val fromDate: LocalDate, val toDate: LocalDate)
