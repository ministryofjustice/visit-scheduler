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
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict.DOUBLE_BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
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
open class SessionService(
  private val sessionDatesUtil: SessionDatesUtil,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val applicationRepository: ApplicationRepository,
  private val sessionSlotRepository: SessionSlotRepository,
  private val prisonerService: PrisonerService,
  @Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @Value("\${policy.session.non-association.filter:false}")
  private val policyFilterNonAssociation: Boolean,
  private val sessionValidator: PrisonerSessionValidator,
  private val prisonerValidationService: PrisonerValidationService,
  private val prisonsService: PrisonsService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  open fun getVisitSessions(
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

    val visitSessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, dateRange.fromDate, dateRange.toDate)
    }.flatten()

    val sessionSlots = getSessionSlots(visitSessions)
    val nonAssociationConflictSessions = getNonAssociationSessions(visitSessions, sessionSlots, prisonerId)
    val doubleBookings = getDoubleBookingSessions(visitSessions, sessionSlots, prisonerId)

    return visitSessions.filterNot {
      filterOutNonAssociations(nonAssociationConflictSessions, it)
    }.filterNot {
      filterOutDoubleBookings(doubleBookings, it)
    }.also {
      addConflicts(it, nonAssociationConflictSessions, doubleBookings)
    }.also {
      populateBookedCount(sessionSlots, it)
    }.sortedWith(compareBy { it.startTimestamp })
  }

  private fun addConflicts(
    it: List<VisitSessionDto>,
    nonAssociationConflictSessions: Set<VisitSessionDto>,
    doubleBookings: List<VisitSessionDto>,
  ) {
    it.forEach {
      // set conflict non association flag
      if (nonAssociationConflictSessions.contains(it)) it.sessionConflicts.add(NON_ASSOCIATION)
      // set conflict double booked flag
      if (doubleBookings.contains(it)) it.sessionConflicts.add(DOUBLE_BOOKED)
    }
  }

  private fun filterOutNonAssociations(
    noAssociationConflictSessions: Set<VisitSessionDto>,
    it: VisitSessionDto,
  ): Boolean = noAssociationConflictSessions.contains(it) && policyFilterNonAssociation

  private fun filterOutDoubleBookings(
    doubleBookings: List<VisitSessionDto>,
    it: VisitSessionDto,
  ): Boolean = doubleBookings.contains(it) && policyFilterDoubleBooking

  private fun getDoubleBookingSessions(visitSessions: List<VisitSessionDto>, sessionSlots: List<SessionSlot>, prisonerId: String): List<VisitSessionDto> {
    val sessionSlotsByKey = sessionSlots.associateBy { it.slotDate.toString() + it.sessionTemplateReference }
    return visitSessions.filter {
      val key = it.startTimestamp.toLocalDate().toString() + it.sessionTemplateReference
      sessionSlotsByKey.containsKey(key) && sessionHasBookingOrApplications(sessionSlotsByKey[key]!!, prisonerId)
    }
  }

  private fun getSessionSlots(sessionTemplates: List<VisitSessionDto>): List<SessionSlot> {
    val sessionDates = sessionTemplates.map { it.startTimestamp.toLocalDate() }.distinct()
    val sessionTemplateReference = sessionTemplates.map { it.sessionTemplateReference }.distinct()

    return sessionSlotRepository.findSessionSlot(sessionDates, sessionTemplateReference)
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
        val prisonerLevels = prisonerService.getLevelsMapForPrisoner(prisonerDetailDto, sessionTemplates)
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

    return listOf()
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

  private fun populateBookedCount(
    sessionSlotIds: List<SessionSlot>,
    sessions: List<VisitSessionDto>,
  ) {
    val sessionSlotIdsByKey = sessionSlotIds.associateBy { it.sessionTemplateReference + it.slotDate.toString() }

    sessions.forEach {
      val visitRestrictionStatsList: List<VisitRestrictionStats> = getVisitRestrictionStats(it, sessionSlotIdsByKey)
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

  private fun getNonAssociationSessions(
    sessions: List<VisitSessionDto>,
    sessionSlots: List<SessionSlot>,
    prisonerId: String,
  ): Set<VisitSessionDto> {
    val sessionSlotsByDate = sessionSlots.groupBy { it.slotDate }

    val prisonerNonAssociationList = prisonerService.getPrisonerNonAssociationList(prisonerId)
    if (prisonerNonAssociationList.isNotEmpty()) {
      val datesWithNonAssociation = getDatesWithNonAssociationVisitsOrApplications(sessionSlotsByDate, prisonerNonAssociationList)

      return sessions.filter {
        datesWithNonAssociation.contains(it.startTimestamp.toLocalDate())
      }.toSet()
    }
    return emptySet()
  }

  private fun getDatesWithNonAssociationVisitsOrApplications(
    sessionSlotsByDate: Map<LocalDate, List<SessionSlot>>,
    prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
  ): MutableSet<LocalDate> {
    val datesWithNonAssociation = mutableSetOf<LocalDate>()
    sessionSlotsByDate.forEach {
      if (sessionSlotHasNonAssociation(it.value, prisonerNonAssociationList)) {
        datesWithNonAssociation.add(it.key)
      }
    }
    return datesWithNonAssociation
  }

  private fun sessionSlotHasNonAssociation(
    sessionSlots: List<SessionSlot>,
    prisonerNonAssociationList: @NotNull List<PrisonerNonAssociationDetailDto>,
  ): Boolean {
    val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(prisonerNonAssociationList)

    if (nonAssociationPrisonerIds.isEmpty() || sessionSlots.isEmpty()) {
      return false
    }

    val sessionSlotIds = sessionSlots.map { it.id }
    if (visitRepository.hasActiveVisitsForDate(
        nonAssociationPrisonerIds,
        sessionSlotIds,
      )
    ) {
      return true
    }

    return applicationRepository.hasActiveApplicationsForDate(
      nonAssociationPrisonerIds,
      sessionSlotIds,
    )
  }

  private fun getNonAssociationPrisonerIds(
    @NotNull prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
  ): List<String> {
    return prisonerNonAssociationList.map { it.otherPrisonerDetails.prisonerNumber }
  }

  private fun sessionHasBookingOrApplications(sessionSlot: SessionSlot, prisonerId: String): Boolean {
    if (visitRepository.hasActiveVisitForDate(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlot.id,
      )
    ) {
      return true
    }

    return applicationRepository.hasReservations(
      prisonerId = prisonerId,
      sessionSlotId = sessionSlot.id,
    )
  }

  private fun getVisitRestrictionStats(session: VisitSessionDto, sessionSlotIdsByKey: Map<String, SessionSlot>): List<VisitRestrictionStats> {
    val slotDate = session.startTimestamp.toLocalDate()
    val sessionSlot = sessionSlotIdsByKey.get(session.sessionTemplateReference + slotDate)
    sessionSlot?.let {
      val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(it.id)
      val restrictionReservedApplicationStats = applicationRepository.getCountOfReservedSessionForOpenOrClosedRestriction(it.id)

      return restrictionBookedStats + restrictionReservedApplicationStats
    }
    return emptyList()
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
      areLocationGroupsIncluded = sessionTemplate.includeLocationGroupType,
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
