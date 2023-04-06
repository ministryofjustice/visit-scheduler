package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.constraints.NotNull
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
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
import java.util.stream.Collectors
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
) {

  @Transactional(readOnly = true)
  fun getVisitSessions(
    prisonCode: String,
    prisonerId: String? = null,
    noticeDaysMin: Long? = null,
    noticeDaysMax: Long? = null,
  ): List<VisitSessionDto> {
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
      inclEnhancedPrivilegeTemplates = prisoner?.let { prisoner.enhanced } ?: true,
    )

    prisoner?.let {
      sessionTemplates = filterByCategory(sessionTemplates, prisoner.category)
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

  private fun filterByCategory(sessionTemplates: List<SessionTemplate>, prisonerCategory: String?): List<SessionTemplate> {
    val hasSessionsWithCategoryGroups = sessionTemplates.any { it.permittedSessionCategoryGroups.isNotEmpty() }
    if (hasSessionsWithCategoryGroups) {
      return sessionTemplates.filter { sessionTemplate ->
        sessionTemplate.permittedSessionCategoryGroups.isEmpty() || isPrisonerCategoryAllowedOnSession(sessionTemplate, prisonerCategory)
      }
    }

    return sessionTemplates
  }

  private fun isPrisonerCategoryAllowedOnSession(sessionTemplate: SessionTemplate, prisonerCategory: String?): Boolean {
    prisonerCategory?.let {
      return getAllowedCategoriesForSessionTemplate(sessionTemplate).any { category ->
        category.equals(prisonerCategory, false)
      }
    }

    // if prisoner category is null - return false as prisoner should not be allowed on restricted category sessions
    return false
  }

  private fun getAllowedCategoriesForSessionTemplate(sessionTemplate: SessionTemplate): Set<String> {
    return sessionTemplate.permittedSessionCategoryGroups.stream()
      .flatMap { it.sessionCategories.stream() }
      .map { it.prisonerCategoryType.code }
      .collect(Collectors.toSet())
  }

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    requestedBookableStartDate: LocalDate,
    requestedBookableEndDate: LocalDate,
  ): List<VisitSessionDto> {
    val firstBookableSessionDay = getFirstBookableSessionDay(requestedBookableStartDate, sessionTemplate.validFromDate, sessionTemplate.dayOfWeek)
    val lastBookableSessionDay = getLastBookableSession(requestedBookableEndDate, sessionTemplate.validToDate)

    if (firstBookableSessionDay <= lastBookableSessionDay) {
      return this.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate)
        .map { date ->
          VisitSessionDto(
            sessionTemplateReference = sessionTemplate.reference,
            prisonCode = sessionTemplate.prison.code,
            startTimestamp = LocalDateTime.of(date, sessionTemplate.startTime),
            openVisitCapacity = sessionTemplate.openCapacity,
            closedVisitCapacity = sessionTemplate.closedCapacity,
            endTimestamp = LocalDateTime.of(date, sessionTemplate.endTime),
            capacityGroup = sessionTemplate.capacityGroup,
            visitType = sessionTemplate.visitType,
          )
        }
        .toList()
    }

    return emptyList()
  }

  private fun calculateDates(firstBookableSessionDay: LocalDate, lastBookableSessionDay: LocalDate, sessionTemplate: SessionTemplate): Stream<LocalDate> {
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

  private fun filterSessionsTemplatesForLocation(sessionTemplates: List<SessionTemplate>, prisonerId: String?, prisonCode: String): List<SessionTemplate> {
    val hasSessionsWithLocationGroups = sessionTemplates.any { it.permittedSessionGroups.isNotEmpty() }
    if (hasSessionsWithLocationGroups) {
      prisonerId?.let { it ->
        val prisonerDetailDto = prisonerService.getPrisonerHousingLocation(it, prisonCode)
        prisonerDetailDto?.let { prisonerDetail ->
          val prisonerLevels = prisonerService.getLevelsMapForPrisoner(prisonerDetail)
          return sessionTemplates.filter { sessionTemplate ->
            sessionValidator.isSessionAvailableToPrisoner(prisonerLevels, sessionTemplate)
          }
        }
        return listOf()
      }
    }

    return sessionTemplates
  }

  private fun filterPrisonerConflict(sessions: List<VisitSessionDto>, prisonerId: String, noAssociationConflictSessions: List<VisitSessionDto>): List<VisitSessionDto> {
    return sessions.filterNot {
      (policyFilterNonAssociation && noAssociationConflictSessions.contains(it)) ||
        (policyFilterDoubleBooking && sessionHasBooking(it, prisonerId))
    }
  }

  private fun populateConflict(sessions: List<VisitSessionDto>, prisonerId: String, noAssociationConflictSessions: List<VisitSessionDto>) {
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

  private fun getCountsByVisitRestriction(visitRestriction: VisitRestriction, visitRestrictionStatsList: List<VisitRestrictionStats>): Int {
    return visitRestrictionStatsList.stream().filter { visitRestriction == it.visitRestriction }.mapToInt(VisitRestrictionStats::count).sum()
  }

  private fun getNoAssociationConflictSessions(sessions: List<VisitSessionDto>, prisonerId: String): List<VisitSessionDto> {
    val offenderNonAssociationList = prisonerService.getOffenderNonAssociationList(prisonerId)
    return sessions.filter {
      sessionHasNonAssociation(it, offenderNonAssociationList)
    }
  }

  private fun sessionHasNonAssociation(session: VisitSessionDto, offenderNonAssociationList: @NotNull List<OffenderNonAssociationDetailDto>): Boolean {
    if (offenderNonAssociationList.isNotEmpty()) {
      val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(session.startTimestamp.toLocalDate(), offenderNonAssociationList)
      val startDateTimeFilter = if (policyNonAssociationWholeDay) session.startTimestamp.toLocalDate().atStartOfDay() else session.startTimestamp
      val endDateTimeFilter = if (policyNonAssociationWholeDay) session.endTimestamp.toLocalDate().atTime(LocalTime.MAX) else session.endTimestamp

      // Any Non-association withing the session period && Non-association has a RESERVED or BOOKED booking.
      // We could also include ATTENDED booking but as prisons have a minimum notice period they can be ignored.
      return visitRepository.hasActiveVisits(nonAssociationPrisonerIds, session.prisonCode, startDateTimeFilter, endDateTimeFilter)
    }

    return false
  }

  private fun getNonAssociationPrisonerIds(startTimestamp: LocalDate, @NotNull offenderNonAssociationList: List<OffenderNonAssociationDetailDto>): List<String> {
    return offenderNonAssociationList.filter { isDateWithinRange(startTimestamp, it.effectiveDate, it.expiryDate) }.map { it.offenderNonAssociation.offenderNo }
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
      capacityGroup = session.capacityGroup,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
      expiredDateAndTime = visitService.getReservedExpiredDateAndTime(),
    )

    val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
      prisonCode = session.prisonCode,
      capacityGroup = session.capacityGroup,
      startDateTime = session.startTimestamp,
      endDateTime = session.endTimestamp,
    )

    return restrictionReservedStats + restrictionBookedStats
  }

  private fun isDateWithinRange(sessionDate: LocalDate, startDate: LocalDate, endDate: LocalDate? = null) =
    sessionDate >= startDate && (endDate == null || sessionDate <= endDate)

  fun getSessionCapacity(prisonCode: String, sessionDate: LocalDate, sessionStartTime: LocalTime, sessionEndTime: LocalTime): List<SessionCapacityDto> {
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

    val capacityGroups = sessionTemplates.groupBy { it.capacityGroup }
    return capacityGroups.map { (capacityGroup, itemsInGroup) -> SessionCapacityDto(itemsInGroup) }
  }

  private fun filterSessionsTemplatesForDate(date: LocalDate, sessionTemplates: List<SessionTemplate>): List<SessionTemplate> {
    return sessionTemplates.filter { sessionTemplate ->
      sessionDatesUtil.isBiWeeklySessionActiveForDate(date, sessionTemplate)
    }
  }

  fun getSessionSchedule(prisonCode: String, scheduleDate: LocalDate): List<SessionScheduleDto> {
    var sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesForSession(
      prisonCode,
      scheduleDate,
      scheduleDate.dayOfWeek,
    )

    sessionTemplates = filterSessionsTemplatesForDate(scheduleDate, sessionTemplates)

    return sessionTemplates.map { sessionTemplate -> createSessionInfoDto(sessionTemplate) }.toList()
  }

  private fun createSessionInfoDto(sessionTemplate: SessionTemplate): SessionScheduleDto {
    val sessionTemplateFrequency = getSessionTemplateFrequency(sessionTemplate)

    return SessionScheduleDto(
      sessionTemplateReference = sessionTemplate.reference,
      startTime = sessionTemplate.startTime,
      endTime = sessionTemplate.endTime,
      capacity = SessionCapacityDto(sessionTemplate),
      prisonerLocationGroupNames = sessionTemplate.permittedSessionGroups.map { it.name }.toList(),
      sessionTemplateFrequency = sessionTemplateFrequency,
      sessionTemplateEndDate = sessionTemplate.validToDate,
      enhanced = sessionTemplate.enhanced,
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
