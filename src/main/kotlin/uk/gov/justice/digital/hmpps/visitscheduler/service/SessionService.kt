package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import jakarta.validation.constraints.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.DOUBLE_BOOKING_OR_RESERVATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict.NON_ASSOCIATION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AvailableVisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.CapacityNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.function.Predicate
import java.util.stream.Stream

@Service
@Transactional
class SessionService(
  private val sessionDatesUtil: SessionDatesUtil,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val visitRepository: VisitRepository,
  private val sessionSlotRepository: SessionSlotRepository,
  private val prisonerService: PrisonerService,
  private val prisonerValidationService: PrisonerValidationService,
  private val prisonsService: PrisonsService,
  private val applicationService: ApplicationService,
  private val prisonerSessionValidationService: PrisonerSessionValidationService,
  @Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @Value("\${policy.session.non-association.filter:false}")
  private val policyFilterNonAssociation: Boolean,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getIndividualVisitSession(prisonCode: String, sessionDate: LocalDate, sessionTemplateReference: String): VisitSessionDto {
    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesForSession(
      prisonCode,
      sessionDate,
      sessionDate.dayOfWeek,
    ).filter { it.reference == sessionTemplateReference }

    if (sessionTemplates.isEmpty()) {
      throw TemplateNotFoundException("Template with reference: $sessionTemplateReference not found on call to getVisitSession")
    }

    val visitSessions = sessionTemplates.map { sessionTemplate ->
      VisitSessionDto(
        sessionTemplateReference = sessionTemplate.reference,
        prisonCode = sessionTemplate.prison.code,
        startTimestamp = LocalDateTime.of(sessionDate, sessionTemplate.startTime),
        openVisitCapacity = sessionTemplate.openCapacity,
        closedVisitCapacity = sessionTemplate.closedCapacity,
        endTimestamp = LocalDateTime.of(sessionDate, sessionTemplate.endTime),
        visitRoom = sessionTemplate.visitRoom,
        visitType = sessionTemplate.visitType,
      )
    }.also {
      val sessionSlots = getSessionSlots(it)
      populateBookedCount(sessionSlotIds = sessionSlots, sessions = it, includeReservations = false)
    }

    return visitSessions.first()
  }

  @Transactional(readOnly = true)
  fun getAllVisitSessions(
    prisonCode: String,
    prisonerId: String,
    currentApplicationReference: String? = null,
    minOverride: Int? = null,
    maxOverride: Int? = null,
    usernameToExcludeFromReservedApplications: String? = null,
    userType: UserType,
  ): List<VisitSessionDto> {
    if (userType != UserType.STAFF) {
      throw ValidationException("Cannot call endpoint for userType - $userType")
    }

    val prison = prisonsService.findPrisonByCode(prisonCode)
    val dateRange = getDateRange(prison, minOverride, maxOverride)

    return getVisitSessions(
      prison = prison,
      prisonerId = prisonerId,
      dateRange = dateRange,
      excludedApplicationReference = currentApplicationReference,
      usernameToExcludeFromReservedApplications = usernameToExcludeFromReservedApplications,
      userType = userType,
    )
  }

  private fun getVisitSessions(
    prison: Prison,
    prisonerId: String,
    dateRange: DateRange,
    excludedApplicationReference: String? = null,
    usernameToExcludeFromReservedApplications: String? = null,
    userType: UserType,
  ): List<VisitSessionDto> {
    val prisonCode = prison.code
    LOG.debug("Enter getVisitSessions prisonCode:${prison.code}, prisonerId : $prisonerId ")

    // ensure the prisoner - if supplied belongs to the same prison as supplied prisonCode
    val prisoner = prisonerService.getPrisoner(prisonerId).also {
      prisonerValidationService.validatePrisonerNotNull(prisonerId, it)
      prisonerValidationService.validatePrisonerIsFromPrison(it!!, prisonCode)
    }!!

    var sessionTemplates = getAllSessionTemplatesForDateRange(prisonCode, dateRange).filter { sessionsByUserClientFilter(userType).test(it) }

    val prisonerHousingLevels = prisonerService.getPrisonerHousingLevels(prisonerId = prisonerId, prisonCode = prisonCode, sessionTemplates = sessionTemplates)

    // get all sessions available to the prisoner
    sessionTemplates = sessionTemplates.filter { sessionTemplate ->
      // checks for location, incentive and category
      prisonerSessionValidationService.isSessionAvailableToPrisoner(sessionTemplates, sessionTemplate, prisoner, prisonerHousingLevels)
    }

    val visitSessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, dateRange.fromDate, dateRange.toDate)
    }.flatten()

    val sessionSlots = getSessionSlots(visitSessions)
    val nonAssociationConflictSessions = getNonAssociationSessions(visitSessions, prisonerId, prison)
    val doubleBookingOrReservationSessions = getDoubleBookingOrReservationSessions(visitSessions, sessionSlots, prisonerId, excludedApplicationReference, usernameToExcludeFromReservedApplications)

    return visitSessions.filterNot {
      hasNonAssociationConflict(nonAssociationConflictSessions, it) && policyFilterNonAssociation
    }.filterNot {
      hasDoubleBookingOrReservationSessions(doubleBookingOrReservationSessions, it) && policyFilterDoubleBooking
    }.also {
      addConflicts(it, nonAssociationConflictSessions, doubleBookingOrReservationSessions)
    }.also {
      populateBookedCount(sessionSlots, it, excludedApplicationReference, usernameToExcludeFromReservedApplications, true)
    }.sortedWith(compareBy { it.startTimestamp })
  }

  @Transactional(readOnly = true)
  fun getOnlyAvailableVisitSessions(
    prisonCode: String,
    prisonerId: String,
    sessionRestriction: SessionRestriction,
    dateRange: DateRange,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
    userType: UserType,
  ): List<AvailableVisitSessionDto> {
    LOG.debug(
      "Enter getAvailableVisitSessions prisonCode:{}, prisonerId : {}, sessionRestriction: {}, dateRange - {}, excludedApplicationReference - {}, excludeReservedApplicationsForUser - {} ",
      prisonCode,
      prisonerId,
      sessionRestriction,
      dateRange,
      excludedApplicationReference,
      usernameToExcludeFromReservedApplications,
    )
    // get all visit sessions for usertype
    val prison = prisonsService.findPrisonByCode(prisonCode)

    val visitSessions = getVisitSessions(
      prison = prison,
      prisonerId = prisonerId,
      dateRange = dateRange,
      excludedApplicationReference = excludedApplicationReference,
      usernameToExcludeFromReservedApplications = usernameToExcludeFromReservedApplications,
      userType = userType,
    )

    // finally filter out sessions without conflicts and with capacity
    return visitSessions.filter {
      hasSessionGotCapacity(it, sessionRestriction).and(it.sessionConflicts.isEmpty())
    }.map { AvailableVisitSessionDto(it, sessionRestriction) }.toList()
  }

  private fun sessionsByUserClientFilter(userType: UserType): Predicate<SessionTemplate> = Predicate {
    it.clients.filter { userClient ->
      userClient.active
    }.map { userClient ->
      userClient.userType
    }
      .contains(userType)
  }

  private fun hasSessionGotCapacity(session: VisitSessionDto, sessionRestriction: SessionRestriction): Boolean = when (sessionRestriction) {
    SessionRestriction.CLOSED -> (session.closedVisitCapacity > 0 && (session.closedVisitCapacity > (session.closedVisitBookedCount ?: 0)))
    SessionRestriction.OPEN -> (session.openVisitCapacity > 0 && (session.openVisitCapacity > (session.openVisitBookedCount ?: 0)))
  }

  private fun addConflicts(
    it: List<VisitSessionDto>,
    nonAssociationConflictSessions: Set<VisitSessionDto>,
    doubleBookingOrReservationSessions: List<VisitSessionDto>,
  ) {
    it.forEach {
      // set conflict non association flag
      if (hasNonAssociationConflict(nonAssociationConflictSessions, it)) it.sessionConflicts.add(NON_ASSOCIATION)
      // set conflict double booked flag
      if (hasDoubleBookingOrReservationSessions(doubleBookingOrReservationSessions, it)) it.sessionConflicts.add(DOUBLE_BOOKING_OR_RESERVATION)
    }
  }

  private fun hasNonAssociationConflict(
    noAssociationConflictSessions: Set<VisitSessionDto>,
    it: VisitSessionDto,
  ): Boolean {
    val noAssociationConflictSessionDates = noAssociationConflictSessions.map { it.startTimestamp.toLocalDate() }
    return noAssociationConflictSessionDates.contains(it.startTimestamp.toLocalDate())
  }

  private fun hasDoubleBookingOrReservationSessions(
    doubleBookingOrReservationSessions: List<VisitSessionDto>,
    it: VisitSessionDto,
  ): Boolean = doubleBookingOrReservationSessions.contains(it)

  private fun getDoubleBookingOrReservationSessions(
    visitSessions: List<VisitSessionDto>,
    sessionSlots: List<SessionSlot>,
    prisonerId: String,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
  ): List<VisitSessionDto> {
    val sessionSlotsByKey = sessionSlots.associateBy { it.slotDate.toString() + it.sessionTemplateReference }
    return visitSessions.filter {
      val key = it.startTimestamp.toLocalDate().toString() + it.sessionTemplateReference
      sessionSlotsByKey.containsKey(key) && sessionHasBookingOrApplications(sessionSlotsByKey[key]!!, prisonerId, excludedApplicationReference, usernameToExcludeFromReservedApplications)
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

  private fun getAllSessionTemplatesForDateRange(prisonCode: String, dateRange: DateRange): List<SessionTemplate> = sessionTemplateRepository.findSessionTemplateMinCapacityBy(
    prisonCode = prisonCode,
    rangeStartDate = dateRange.fromDate,
    rangeEndDate = dateRange.toDate,
  )

  private fun buildVisitSessionsUsingTemplate(
    sessionTemplate: SessionTemplate,
    requestedBookableStartDate: LocalDate,
    requestedBookableEndDate: LocalDate,
  ): List<VisitSessionDto> {
    val firstBookableSessionDay =
      getFirstBookableSessionDay(requestedBookableStartDate, sessionTemplate.validFromDate, sessionTemplate.dayOfWeek, sessionTemplate.weeklyFrequency)
    val lastBookableSessionDay = getLastBookableSession(requestedBookableEndDate, sessionTemplate.validToDate)
    val excludeDates = getExcludeDates(sessionTemplate)

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

  fun getExcludeDates(sessionTemplate: SessionTemplate): Set<LocalDate> {
    val excludeDates = mutableSetOf<LocalDate>()
    excludeDates.addAll(sessionTemplate.excludeDates.map { it.excludeDate })
    excludeDates.addAll(sessionTemplate.prison.excludeDates.map { it.excludeDate })
    return excludeDates.toSet()
  }

  private fun calculateDates(
    firstBookableSessionDay: LocalDate,
    lastBookableSessionDay: LocalDate,
    sessionTemplate: SessionTemplate,
  ): Stream<LocalDate> = sessionDatesUtil.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate)

  private fun getFirstBookableSessionDay(
    bookablePeriodStartDate: LocalDate,
    sessionStartDate: LocalDate,
    sessionDayOfWeek: DayOfWeek,
    sessionFrequency: Int,
  ): LocalDate {
    // Step 1: Adjust the session start date to the correct day of the week.
    val firstDayMatchingDate = adjustDateByDayOfWeek(sessionDayOfWeek, sessionStartDate)

    // Step 2: Calculate the difference in days between this date and the bookable period start date.
    val daysDifference = ChronoUnit.DAYS.between(firstDayMatchingDate, bookablePeriodStartDate)

    // Step 3: Calculate the number of weeks to add to get the firstDayMatchingDate past the bookablePeriodStartDate.
    // If daysDifference is positive, we calculate the number of weeks required.
    // Else we're already on or past the bookablePeriodStartDate so don't add any weeks.
    val weeksToAdd = if (daysDifference > 0) {
      (daysDifference / (sessionFrequency * 7)) + if (daysDifference % (sessionFrequency * 7) > 0) 1 else 0
    } else {
      0
    }

    // Step 4: Return the date after adding the correct number of intervals.
    return firstDayMatchingDate.plusWeeks((weeksToAdd * sessionFrequency))
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
    excludedApplicationReference: String? = null,
    usernameToExcludeFromReservedApplications: String? = null,
    includeReservations: Boolean = true,
  ) {
    val sessionSlotIdsByKey = sessionSlotIds.associateBy { it.sessionTemplateReference + it.slotDate.toString() }

    sessions.forEach {
      val visitRestrictionStatsList: List<VisitRestrictionStats> = getVisitRestrictionStats(it, sessionSlotIdsByKey, excludedApplicationReference, usernameToExcludeFromReservedApplications, includeReservations)
      it.openVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.OPEN, visitRestrictionStatsList)
      it.closedVisitBookedCount = getCountsByVisitRestriction(VisitRestriction.CLOSED, visitRestrictionStatsList)
    }
  }

  private fun getCountsByVisitRestriction(
    visitRestriction: VisitRestriction,
    visitRestrictionStatsList: List<VisitRestrictionStats>,
  ): Int = visitRestrictionStatsList.stream().filter { visitRestriction == it.visitRestriction }
    .mapToInt(VisitRestrictionStats::count).sum()

  private fun getNonAssociationSessions(
    sessions: List<VisitSessionDto>,
    prisonerId: String,
    prison: Prison,
  ): Set<VisitSessionDto> {
    val sessionSlotsByDate = sessions.map { it.startTimestamp.toLocalDate() }

    val prisonerNonAssociationList = prisonerService.getPrisonerNonAssociationList(prisonerId)
    if (prisonerNonAssociationList.isNotEmpty()) {
      val datesWithNonAssociation = getDatesWithNonAssociationVisitsOrApplications(sessionSlotsByDate, prisonerNonAssociationList, prison)

      return sessions.filter {
        datesWithNonAssociation.contains(it.startTimestamp.toLocalDate())
      }.toSet()
    }
    return emptySet()
  }

  private fun getDatesWithNonAssociationVisitsOrApplications(
    sessionSlotDates: List<LocalDate>,
    prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
    prison: Prison,
  ): MutableSet<LocalDate> {
    val datesWithNonAssociation = mutableSetOf<LocalDate>()
    sessionSlotDates.forEach {
      if (sessionSlotHasNonAssociation(it, prisonerNonAssociationList, prison)) {
        datesWithNonAssociation.add(it)
      }
    }
    return datesWithNonAssociation
  }

  private fun sessionSlotHasNonAssociation(
    sessionSlotDate: LocalDate,
    prisonerNonAssociationList: @NotNull List<PrisonerNonAssociationDetailDto>,
    prison: Prison,
  ): Boolean {
    val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(prisonerNonAssociationList)

    if (nonAssociationPrisonerIds.isEmpty()) {
      return false
    }

    if (visitRepository.hasActiveVisitsForDate(
        nonAssociationPrisonerIds,
        sessionSlotDate,
        prison.id,
      )
    ) {
      return true
    }

    return applicationService.hasActiveApplicationsForDate(
      nonAssociationPrisonerIds,
      sessionSlotDate,
      prison.id,
    )
  }

  private fun getNonAssociationPrisonerIds(
    @NotNull prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
  ): List<String> = prisonerNonAssociationList.map { it.otherPrisonerDetails.prisonerNumber }

  private fun sessionHasBookingOrApplications(
    sessionSlot: SessionSlot,
    prisonerId: String,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
  ): Boolean {
    if (visitRepository.hasActiveVisitForSessionSlot(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlot.id,
      )
    ) {
      return true
    }

    return applicationService.hasReservations(
      prisonerId = prisonerId,
      sessionSlotId = sessionSlot.id,
      excludedApplicationReference = excludedApplicationReference,
      usernameToExcludeFromReservedApplications = usernameToExcludeFromReservedApplications,
    )
  }

  private fun getVisitRestrictionStats(
    session: VisitSessionDto,
    sessionSlotIdsByKey: Map<String, SessionSlot>,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
    includeReservations: Boolean,
  ): List<VisitRestrictionStats> {
    val slotDate = session.startTimestamp.toLocalDate()
    val sessionSlot = sessionSlotIdsByKey[session.sessionTemplateReference + slotDate]
    sessionSlot?.let {
      val restrictionBookedStats = visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(it.id)

      val restrictionReservedApplicationStats = if (includeReservations) {
        applicationService.getCountOfReservedSessionForOpenOrClosedRestriction(
          it.id,
          excludedApplicationReference = excludedApplicationReference,
          usernameToExcludeFromReservedApplications,
        )
      } else {
        emptyList()
      }

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
  ): List<SessionTemplate> = sessionTemplates.filter { sessionTemplate ->
    sessionDatesUtil.isActiveForDate(date, sessionTemplate)
  }.sortedWith(
    Comparator.comparing(SessionTemplate::startTime)
      .thenComparing(SessionTemplate::endTime),
  )

  fun getSessionSchedule(prisonCode: String, scheduleDate: LocalDate): List<SessionScheduleDto> = if (prisonsService.isExcludedDate(prisonCode, scheduleDate)) {
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

  private fun createSessionScheduleDto(sessionTemplate: SessionTemplate): SessionScheduleDto = SessionScheduleDto(
    sessionTemplateReference = sessionTemplate.reference,
    sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime, endTime = sessionTemplate.endTime),
    capacity = SessionCapacityDto(sessionTemplate),
    areLocationGroupsInclusive = sessionTemplate.includeLocationGroupType,
    prisonerLocationGroupNames = sessionTemplate.permittedSessionLocationGroups.map { it.name }.toList(),
    prisonerCategoryGroupNames = sessionTemplate.permittedSessionCategoryGroups.map { it.name }.toList(),
    prisonerIncentiveLevelGroupNames = sessionTemplate.permittedSessionIncentiveLevelGroups.map { it.name }.toList(),
    weeklyFrequency = sessionTemplate.weeklyFrequency,
    visitType = sessionTemplate.visitType,
    sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate, validToDate = sessionTemplate.validToDate),
    visitRoom = sessionTemplate.visitRoom,
  )

  private fun adjustDateByDayOfWeek(dayOfWeek: DayOfWeek, startDate: LocalDate): LocalDate {
    if (startDate.dayOfWeek != dayOfWeek) {
      return startDate.with(TemporalAdjusters.next(dayOfWeek))
    }
    return startDate
  }
}

data class DateRange(
  val fromDate: LocalDate,
  val toDate: LocalDate,
)
