package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import jakarta.validation.constraints.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.AvailableVisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.CapacityNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionConflictsUtil
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
  private val sessionConflictsUtil: SessionConflictsUtil,
  @param:Value("\${policy.session.double-booking.filter:false}")
  private val policyFilterDoubleBooking: Boolean,
  @param:Value("\${policy.session.non-association.filter:false}")
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
    LOG.debug("Enter getVisitSessions prisonCode:${prison.code}, prisonerId : $prisonerId")

    // ensure the prisoner - if supplied belongs to the same prison as supplied prisonCode
    val prisoner = prisonerService.getPrisoner(prisonerId).also {
      prisonerValidationService.validatePrisonerNotNull(prisonerId, it)
      prisonerValidationService.validatePrisonerIsFromPrison(it!!, prisonCode)
    }!!

    var sessionTemplates = getAllSessionTemplatesForDateRange(prisonCode, dateRange).filter { sessionsByUserClientFilter(userType).test(it) }

    LOG.debug("Retrieved {} sessions before beginning filtering for prisoner {}, with date range {}", sessionTemplates.size, prisonerId, dateRange)

    val prisonerHousingLevels = prisonerService.getPrisonerHousingLevels(prisonerId = prisonerId, prisonCode = prisonCode, sessionTemplates = sessionTemplates)

    // get all sessions available to the prisoner
    sessionTemplates = sessionTemplates.filter { sessionTemplate ->
      // checks for location, incentive and category
      prisonerSessionValidationService.isSessionAvailableToPrisoner(sessionTemplates, sessionTemplate, prisoner, prisonerHousingLevels)
    }

    var visitSessions = sessionTemplates.map {
      buildVisitSessionsUsingTemplate(it, dateRange.fromDate, dateRange.toDate)
    }.flatten()

    val sessionSlots = getSessionSlots(visitSessions)
    addSessionConflicts(sessionTemplates, visitSessions, prisonerId, prison, sessionSlots, excludedApplicationReference, usernameToExcludeFromReservedApplications)

    visitSessions = visitSessions.also {
      populateBookedCount(sessionSlots, it, excludedApplicationReference, usernameToExcludeFromReservedApplications, true)
    }.sortedWith(compareBy { it.startTimestamp }).also {
      LOG.info("Final count for sessions of ${it.size}, after filtering for prisoner $prisonerId, with date range $dateRange")
    }

    return visitSessions
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
    }.map { AvailableVisitSessionDto(it, sessionRestriction) }.toList().also {
      LOG.info("Returning final count for public filtered sessions ${it.size} for prisonerId - $prisonerId, after applying capacity filtering")
    }
  }

  private fun addSessionConflicts(
    sessionTemplates: List<SessionTemplate>,
    visitSessions: List<VisitSessionDto>,
    prisonerId: String,
    prison: Prison,
    sessionSlots: List<SessionSlot>,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
  ): List<VisitSessionDto> {
    val nonAssociationPrisonerIds = getNonAssociationPrisonerIds(prisonerService.getPrisonerNonAssociationList(prisonerId))
    val sessionSlotDates = visitSessions.map { it.startTimestamp.toLocalDate() }
    val nonAssociationConflictSessions = getNonAssociationVisitsOrApplications(sessionSlotDates, nonAssociationPrisonerIds, prison)
    val doubleBookingOrReservationSessions = getDoubleBookingOrReservationSessions(visitSessions, sessionSlots, prisonerId, excludedApplicationReference, usernameToExcludeFromReservedApplications)

    val prisonExcludeDates = prison.excludeDates.map { it.excludeDate }
    val sessionsExcludeDates = sessionTemplates.flatMap { it.excludeDates }
    visitSessions.forEach { session ->
      val excludedDatesForSession = sessionsExcludeDates.filter { (it.sessionTemplate.reference == session.sessionTemplateReference) }.map { it.excludeDate }
      sessionConflictsUtil.addSessionConflicts(session, nonAssociationConflictSessions, doubleBookingOrReservationSessions, prisonExcludeDates, excludedDatesForSession)
    }

    return visitSessions
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

  private fun getDoubleBookingOrReservationSessions(
    visitSessions: List<VisitSessionDto>,
    sessionSlots: List<SessionSlot>,
    prisonerId: String,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
  ): List<DoubleBookedConflictSessionDto> {
    val doubleBookingOrReservationSessions = mutableListOf<DoubleBookedConflictSessionDto>()
    val sessionSlotsByKey = sessionSlots.associateBy { it.slotDate.toString() + it.sessionTemplateReference }
    visitSessions.forEach { visitSession ->
      val key = visitSession.startTimestamp.toLocalDate().toString() + visitSession.sessionTemplateReference
      if (sessionSlotsByKey.containsKey(key)) {
        val sessionSlot = sessionSlotsByKey[key]!!
        val bookedVisit = getBookedVisitForSessionSlot(sessionSlot, prisonerId)
        if (bookedVisit != null) {
          sessionSlot.sessionTemplateReference?.let { sessionTemplateReference ->
            doubleBookingOrReservationSessions.add(DoubleBookedConflictSessionDto(reference = bookedVisit.reference, conflictType = SessionConflictType.VISIT, visitSubStatus = bookedVisit.visitSubStatus, sessionDate = bookedVisit.sessionSlot.slotStart.toLocalDate(), sessionTemplateReference = sessionTemplateReference))
          }
        } else {
          if (sessionHasDoubleBookedApplications(sessionSlot, prisonerId, excludedApplicationReference, usernameToExcludeFromReservedApplications)) {
            sessionSlot.sessionTemplateReference?.let { sessionTemplateReference ->
              doubleBookingOrReservationSessions.add(DoubleBookedConflictSessionDto(conflictType = SessionConflictType.APPLICATION, sessionDate = sessionSlot.slotStart.toLocalDate(), sessionTemplateReference = sessionTemplateReference, reference = null, visitSubStatus = null))
            }
          }
        }
      }
    }

    // TODO - add application check
    return doubleBookingOrReservationSessions
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

    // add 1 to the policyNoticeDaysMin to ensure we are adding whole days
    val min = minOverride ?: (prison.policyNoticeDaysMin.plus(1))
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
            visitRoom = sessionTemplate.visitRoom,
            visitType = sessionTemplate.visitType,
          )
        }
        .toList()
    }

    return listOf()
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

  private fun getNonAssociationVisitsOrApplications(
    sessionSlotDates: List<LocalDate>,
    nonAssociationPrisonerIds: List<String>,
    prison: Prison,
  ): List<NonAssociationConflictSessionDto> {
    val nonAssociationConflictSessions = mutableListOf<NonAssociationConflictSessionDto>()
    nonAssociationPrisonerIds.forEach { nonAssociationPrisonerId ->
      visitRepository.getBookedVisitsForPrisonerAndDates(prisonerId = nonAssociationPrisonerId, sessionDates = sessionSlotDates, prisonId = prison.id).forEach { visit ->
        nonAssociationConflictSessions.add(NonAssociationConflictSessionDto(prisonerId = nonAssociationPrisonerId, conflictType = SessionConflictType.VISIT, reference = visit.reference, sessionDate = visit.sessionSlot.slotDate))
      }
    }

    nonAssociationPrisonerIds.forEach { nonAssociationPrisonerId ->
      applicationService.getInProgressApplicationsForPrisonerAndDates(nonAssociationPrisonerId, sessionSlotDates, prisonId = prison.id).forEach { application ->
        nonAssociationConflictSessions.add(NonAssociationConflictSessionDto(prisonerId = nonAssociationPrisonerId, conflictType = SessionConflictType.APPLICATION, sessionDate = application.sessionSlot.slotDate, reference = null))
      }
    }

    return nonAssociationConflictSessions
  }

  private fun getNonAssociationPrisonerIds(
    @NotNull prisonerNonAssociationList: List<PrisonerNonAssociationDetailDto>,
  ): List<String> = prisonerNonAssociationList.map { it.otherPrisonerDetails.prisonerNumber }

  private fun sessionHasDoubleBookedApplications(
    sessionSlot: SessionSlot,
    prisonerId: String,
    excludedApplicationReference: String?,
    usernameToExcludeFromReservedApplications: String?,
  ): Boolean {
    /*if (visitRepository.hasActiveVisitForSessionSlot(
        prisonerId = prisonerId,
        sessionSlotId = sessionSlot.id,
      )
    ) {
      return true
    }*/

    return applicationService.hasReservations(
      prisonerId = prisonerId,
      sessionSlotId = sessionSlot.id,
      excludedApplicationReference = excludedApplicationReference,
      usernameToExcludeFromReservedApplications = usernameToExcludeFromReservedApplications,
    )
  }

  private fun getBookedVisitForSessionSlot(
    sessionSlot: SessionSlot,
    prisonerId: String,
  ): Visit? = visitRepository.getActiveVisitForSessionSlot(
    prisonerId = prisonerId,
    sessionSlotId = sessionSlot.id,
  )

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
    areCategoryGroupsInclusive = sessionTemplate.includeCategoryGroupType,
    prisonerCategoryGroupNames = sessionTemplate.permittedSessionCategoryGroups.map { it.name }.toList(),
    areIncentiveGroupsInclusive = sessionTemplate.includeIncentiveGroupType,
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

data class NonAssociationConflictSessionDto(
  val prisonerId: String,
  val conflictType: SessionConflictType,
  val reference: String?,
  val sessionDate: LocalDate,
)

data class DoubleBookedConflictSessionDto(
  val reference: String?,
  val sessionDate: LocalDate,
  val conflictType: SessionConflictType,
  val visitSubStatus: VisitSubStatus?,
  val sessionTemplateReference: String,
)

enum class SessionConflictType {
  APPLICATION,
  VISIT,
}
