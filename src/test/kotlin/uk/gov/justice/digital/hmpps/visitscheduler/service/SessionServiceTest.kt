package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.projection.SpelAwareProxyProjectionFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OtherPrisonerDetails
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionSlotEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.prison
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionSlot
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionSlotRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@ExtendWith(MockitoExtension::class)
class SessionServiceTest {

  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val sessionSlotRepository = mock<SessionSlotRepository>()
  private val prisonerService = mock<PrisonerService>()
  private val prisonerValidationService = mock<PrisonerValidationService>()
  private val sessionDatesUtil = SessionDatesUtil()
  private val prisonsService = mock<PrisonsService>()
  private val applicationService = mock<ApplicationService>()
  private val sessionValidationService = mock<PrisonerSessionValidationService>()

  private lateinit var sessionService: SessionService

  private val currentDate = LocalDate.now()

  private val prisonCode = "MDI"
  private val noticeDaysMin = 0
  private val noticeDaysMax = 100
  private val prisonerId = "AA1234BB"

  @BeforeEach
  fun beforeEachTestSetup() {
    whenever(prisonerService.getPrisoner(any())).thenReturn(PrisonerDto(prisonerId, "john", "smith", "C", IncentiveLevel.STANDARD, prisonCode))
    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(PrisonEntityHelper.createPrison(prisonCode, policyNoticeDaysMin = noticeDaysMin, policyNoticeDaysMax = noticeDaysMax))

    whenever(prisonerService.getPrisonerHousingLocation(any(), any())).thenReturn(
      PrisonerHousingLocationsDto(
        levels = listOf(),
      ),
    )

    whenever(prisonerService.getLevelsMapForPrisoner(any(), any())).thenReturn(mutableMapOf<PrisonerHousingLevels, String?>())
    whenever(sessionValidationService.isSessionAvailableToPrisoner(any(), any(), any(), any())).thenReturn(true)
  }

  private fun mockSessionTemplateRepositoryResponse(response: List<SessionTemplate>, incentiveLevel: IncentiveLevel? = null, category: String? = null) {
    whenever(
      prisonerService.getPrisoner(any()),
    ).thenReturn(PrisonerDto(prisonerId = prisonerId, firstName = "john", lastName = "smith", category = category, incentiveLevel = incentiveLevel))

    whenever(
      sessionTemplateRepository.findSessionTemplateMinCapacityBy(
        prisonCode = prisonCode,
        rangeStartDate = currentDate.plusDays(noticeDaysMin.toLong().plus(1)),
        rangeEndDate = currentDate.plusDays(noticeDaysMax.toLong()),
      ),
    ).thenReturn(response)
  }

  private fun mockSessionSlotRepositoryResponse(sessionSlot: SessionSlot) {
    whenever(
      sessionSlotRepository.findSessionSlot(
        listOf(sessionSlot.slotDate),
        listOf(sessionSlot.sessionTemplateReference!!),
      ),
    ).thenReturn(listOf(sessionSlot))
  }

  private fun mockVisitRepositoryCountResponse(visits: List<Visit>, sessionTemplate: SessionTemplate, sessionSlot: SessionSlot? = null) {
    val startDateTime = currentDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek)).atTime(sessionTemplate.startTime)

    whenever(
      sessionSlotRepository.findSessionSlotId(
        sessionTemplateReference = sessionTemplate.reference,
        startDateTime.toLocalDate(),
      ),
    ).thenReturn(sessionSlot?.id)

    sessionSlot?.let {
      whenever(
        visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(it.id),
      ).thenReturn(getVisitRestrictionStatsList(visits))
    }
  }

  private fun getVisitRestrictionStatsList(visits: List<Visit>): List<VisitRestrictionStats> = listOf(getVisitRestrictionStats(visits, OPEN), getVisitRestrictionStats(visits, CLOSED))

  private fun getVisitRestrictionStats(visits: List<Visit>, visitRestriction: VisitRestriction): VisitRestrictionStats {
    val factory: ProjectionFactory = SpelAwareProxyProjectionFactory()
    val backingMap: MutableMap<String, Any> = HashMap()
    backingMap["visitRestriction"] = visitRestriction
    backingMap["count"] = visits.count { it.visitRestriction == visitRestriction }
    return factory.createProjection(VisitRestrictionStats::class.java, backingMap)
  }

  private fun mockSessionSlots(
    sessionTemplate: SessionTemplate,
  ): List<SessionSlot> {
    val slotDate = sessionTemplate.validFromDate.plusDays(1)
    val slotStart = slotDate.atTime(sessionTemplate.startTime)
    val slotEnd = slotDate.atTime(sessionTemplate.endTime)
    val slots = listOf(SessionSlot(sessionTemplate.reference, sessionTemplate.prisonId, slotDate, slotStart, slotEnd))

    whenever(sessionSlotRepository.findSessionSlot(listOf(slotDate), listOf(sessionTemplate.reference))).thenReturn(
      slots,
    )
    return slots
  }

  @Nested
  @DisplayName("simple session generation")
  inner class SlotGeneration {
    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionDatesUtil = sessionDatesUtil,
        sessionTemplateRepository = sessionTemplateRepository,
        visitRepository = visitRepository,
        sessionSlotRepository = sessionSlotRepository,
        prisonerService = prisonerService,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
        applicationService = applicationService,
        prisonerSessionValidationService = sessionValidationService,
      )
    }

    @Test
    fun `a weekly session will return 6 sessions including today and valid to date`() {
      // Given
      val weeklySession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(5),
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = FRIDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      val fridayAfter = currentDate.with(TemporalAdjusters.next(weeklySession.dayOfWeek)).atTime(weeklySession.startTime)

      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertDate(sessions[0].startTimestamp, fridayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertDate(sessions[1].startTimestamp, fridayAfter.plusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertDate(sessions[2].startTimestamp, fridayAfter.plusWeeks(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertDate(sessions[3].startTimestamp, fridayAfter.plusWeeks(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertDate(sessions[4].startTimestamp, fridayAfter.plusWeeks(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
    }

    @Test
    fun `sessions are consistently generated, weekly sessions always fall on the same day regardless of date of generation`() {
      // Given
      val weeklySession = sessionTemplate(
        validFromDate = currentDate,
        // 5 weeks from today
        validToDate = currentDate.plusWeeks(5),
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = WEDNESDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      val wednesdayAfter = currentDate.with(TemporalAdjusters.next(weeklySession.dayOfWeek)).atTime(weeklySession.startTime)
      assertDate(sessions[0].startTimestamp, wednesdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), WEDNESDAY)
      assertDate(sessions[1].startTimestamp, wednesdayAfter.plusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), WEDNESDAY)
      assertDate(sessions[2].startTimestamp, wednesdayAfter.plusWeeks(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), WEDNESDAY)
      assertDate(sessions[3].startTimestamp, wednesdayAfter.plusWeeks(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), WEDNESDAY)
      assertDate(sessions[4].startTimestamp, wednesdayAfter.plusWeeks(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), WEDNESDAY)
    }

    @Test
    fun `a single session will return 1 session`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        // future time
        startTime = LocalTime.parse("11:30"),
        // future time
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val mondayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
    }

    @Test
    fun `all sessions are on past dates, no sessions are returned`() {
      // Given
      val dailySession = sessionTemplate(
        validFromDate = currentDate.minusDays(8),
        validToDate = currentDate.minusDays(1),
        dayOfWeek = MONDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(dailySession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(0)
    }

    @Test
    fun `Single Session without Visit has zero Open and zero Closed slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        // future time
        startTime = LocalTime.parse("11:30"),
        // future time
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Single Session with BOOKED Visit and OPEN and CLOSED restriction has booked slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        // future time
        startTime = LocalTime.parse("11:30"),
        // future time
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val prison = PrisonEntityHelper.createPrison()

      val slotDate = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek))

      val sessionSlot = SessionSlotEntityHelper.createSessionSlot(
        prisonId = prison.id,
        sessionTemplateReference = singleSession.reference,
        slotDate = slotDate,
        slotTime = LocalTime.of(11, 30),
        slotEndTime = LocalTime.of(12, 30),
      )

      mockSessionSlotRepositoryResponse(sessionSlot)

      val openVisit1 = Visit(
        prisonerId = prisonerId,
        sessionSlot = sessionSlot,
        sessionSlotId = sessionSlot.id,
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitSubStatus = VisitSubStatus.AUTO_APPROVED,
        visitRestriction = OPEN,
        visitRoom = "1",
        userType = STAFF,
      )

      val openVisit2 = Visit(
        prisonerId = prisonerId,
        sessionSlot = sessionSlot,
        sessionSlotId = sessionSlot.id,
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitSubStatus = VisitSubStatus.AUTO_APPROVED,
        visitRestriction = OPEN,
        visitRoom = "1",
        userType = STAFF,
      )

      val closedVisit = Visit(
        prisonerId = prisonerId,
        sessionSlot = sessionSlot,
        sessionSlotId = sessionSlot.id,
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitSubStatus = VisitSubStatus.AUTO_APPROVED,
        visitRestriction = CLOSED,
        visitRoom = "1",
        userType = STAFF,
      )
      mockVisitRepositoryCountResponse(listOf(openVisit1, openVisit2, closedVisit), singleSession, sessionSlot)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(2)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Sessions with UNKNOWN restriction Visits has booked slot counts of ZERO`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        // future time
        startTime = LocalTime.parse("11:30"),
        // future time
        endTime = LocalTime.parse("12:30"),
      )

      val prison = PrisonEntityHelper.createPrison()

      val sessionSlot = SessionSlotEntityHelper.createSessionSlot(
        prisonId = prison.id,
        sessionTemplateReference = singleSession.reference,
        slotDate = currentDate,
        slotTime = LocalTime.of(11, 30),
        slotEndTime = LocalTime.of(12, 30),
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        sessionSlot = sessionSlot,
        sessionSlotId = 1,
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitSubStatus = VisitSubStatus.AUTO_APPROVED,
        visitRestriction = UNKNOWN,
        visitRoom = "1",
        userType = STAFF,
      )

      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockVisitRepositoryCountResponse(listOf(closedVisit), singleSession, sessionSlot)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Sessions with no Visits has booked slot counts of ZERO`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        // future time
        startTime = LocalTime.parse("11:30"),
        // future time
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // no BOOKED or RESERVED visits
      val noVisitsBookedOrReserved = emptyList<Visit>()

      mockVisitRepositoryCountResponse(noVisitsBookedOrReserved, singleSession)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("Available slots including conflicts")
  inner class IncludeConflicts {

    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionDatesUtil = sessionDatesUtil,
        sessionTemplateRepository = sessionTemplateRepository,
        visitRepository = visitRepository,
        sessionSlotRepository = sessionSlotRepository,
        prisonerService = prisonerService,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        prisonerSessionValidationService = sessionValidationService,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
        applicationService = applicationService,
      )
    }

    @Test
    fun `session does not contain conflicts when a prisoner has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonerService.getPrisonerNonAssociationList(prisonerId),
      ).thenReturn(PrisonerNonAssociationDetailsDto().nonAssociations)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      val mondayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when a prisoner has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = FRIDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockGetPrisonerNonAssociation(prisonerId, associationId)

      whenever(sessionSlotRepository.findSessionSlot(anyList(), anyList())).thenReturn(
        listOf(),
      )

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      val fridayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)

      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, fridayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when a prisoner has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"
      val validFromDate = currentDate.plusDays(noticeDaysMin.toLong())
      val dayOfWeek = validFromDate.plusDays(1).dayOfWeek

      val prison = prison()
      val singleSession = sessionTemplate(
        validFromDate = currentDate.plusDays(noticeDaysMin.toLong()),
        validToDate = validFromDate.plusWeeks(1),
        dayOfWeek = dayOfWeek,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        prison = prison,
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))
      mockGetPrisonerNonAssociation(prisonerId, associationId)

      val expectedAssociations = listOf(associationId)

      mockSessionSlots(singleSession)
      val saturdayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      val slotDate = saturdayAfter.toLocalDate()
      whenever(visitRepository.hasActiveVisitsForDate(expectedAssociations, slotDate, prison.id))
        .thenReturn(
          true,
          false,
        )

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dayOfWeek)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts.first()).isEqualTo(SessionConflict.NON_ASSOCIATION)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when a prisoner has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val validFromDate = currentDate.plusDays(noticeDaysMin.toLong())
      val dayOfWeek = validFromDate.plusDays(1).dayOfWeek

      val singleSession = sessionTemplate(
        validFromDate = validFromDate,
        validToDate = validFromDate.plusWeeks(1),
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = dayOfWeek,
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockSessionSlots(singleSession)

      whenever(
        prisonerService.getPrisonerNonAssociationList(prisonerId),
      ).thenReturn(PrisonerNonAssociationDetailsDto().nonAssociations)

      whenever(visitRepository.hasActiveVisitForSessionSlot(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      val saturdayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dayOfWeek)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts.first()).isEqualTo(SessionConflict.DOUBLE_BOOKING_OR_RESERVATION)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when a prisoner non-association NOT FOUND`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonerService.getPrisonerNonAssociationList(prisonerId),
      ).thenReturn(emptyList())

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      val mondayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `get sessions throws WebClientResponseException for BAD REQUEST`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonerService.getPrisonerNonAssociationList(prisonerId),
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null),
      )

      // When
      assertThrows<WebClientResponseException> {
        sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)
      }

      // Then
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `when prisonId and prisonerId do not match get sessions throws PrisonerNotInSuppliedPrisonException`() {
      // Given
      val prisonerId = "A1234AA"
      val incorrectPrisonCode = "ABC"
      val prisonerDto = PrisonerDto(prisonerId = prisonerId, firstName = "john", lastName = "smith", prisonCode = incorrectPrisonCode)

      whenever(
        prisonerService.getPrisoner(prisonerId),
      ).thenReturn(prisonerDto)

      whenever(
        prisonerValidationService.validatePrisonerIsFromPrison(prisonerDto, prisonCode),
      ).thenCallRealMethod()

      // When
      // the prison code being passed is not the same as the prisoners details on Prison API
      assertThrows<PrisonerNotInSuppliedPrisonException> {
        sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)
      }

      // Then
      Mockito.verify(prisonerValidationService, times(1)).validatePrisonerIsFromPrison(prisonerDto, prisonCode)
    }
  }

  @Nested
  @DisplayName("Available slots exclude conflicts")
  inner class ExcludeConflicts {

    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionDatesUtil = sessionDatesUtil,
        sessionTemplateRepository = sessionTemplateRepository,
        visitRepository = visitRepository,
        sessionSlotRepository = sessionSlotRepository,
        prisonerService = prisonerService,
        policyFilterDoubleBooking = true,
        policyFilterNonAssociation = true,
        prisonerSessionValidationService = sessionValidationService,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
        applicationService = applicationService,
      )
    }

    @Test
    fun `all sessions are returned when a prisoner has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockGetPrisonerNonAssociation(prisonerId, "associationID")

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val mondayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `only available sessions are returned when a prisoner has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockGetPrisonerNonAssociation(prisonerId, associationId)

      whenever(visitRepository.hasActiveVisitForSessionSlot(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(false)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `only available sessions are returned when a prisoner has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))
      mockGetPrisonerNonAssociation(prisonerId, associationId)
      val slotDate = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek))

      val sessionSlot = SessionSlotEntityHelper.createSessionSlot(
        prisonId = singleSession.prisonId,
        sessionTemplateReference = singleSession.reference,
        slotDate = slotDate,
        slotTime = LocalTime.of(11, 30),
        slotEndTime = LocalTime.of(12, 30),
      )

      mockSessionSlotRepositoryResponse(sessionSlot)

      whenever(visitRepository.hasActiveVisitForSessionSlot(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `only available sessions are returned when a prisoner has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockGetPrisonerNonAssociation(prisonerId, "associationID")
      val slotDate = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek))
      val sessionSlot = SessionSlotEntityHelper.createSessionSlot(
        prisonId = singleSession.prisonId,
        sessionTemplateReference = singleSession.reference,
        slotDate = slotDate,
        slotTime = LocalTime.of(11, 30),
        slotEndTime = LocalTime.of(12, 30),
      )
      mockSessionSlotRepositoryResponse(sessionSlot)

      whenever(visitRepository.hasActiveVisitForSessionSlot(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `when multiple sessions prison api get prisoners is only called once`() {
      // Given
      val prisonerId = "A1234AA"

      val firstSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )

      val secondSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(2),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )

      mockSessionTemplateRepositoryResponse(listOf(firstSession, secondSession))

      // When
      val sessions = sessionService.getAllVisitSessions(prisonCode, prisonerId, userType = STAFF)

      // Then
      assertThat(sessions).size().isEqualTo(3)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }
  }

  private fun assertDate(localDateTime: LocalDateTime, expectedlyDateTime: String, dayOfWeek: DayOfWeek) {
    assertThat(localDateTime).isEqualTo(LocalDateTime.parse(expectedlyDateTime))
    assertThat(localDateTime.dayOfWeek).isEqualTo(dayOfWeek)
  }

  private fun mockGetPrisonerNonAssociation(prisonerId: String, associationId: String) {
    whenever(
      prisonerService.getPrisonerNonAssociationList(prisonerId),
    ).thenReturn(
      PrisonerNonAssociationDetailsDto(
        listOf(
          PrisonerNonAssociationDetailDto(
            otherPrisonerDetails = OtherPrisonerDetails(prisonerNumber = associationId),
          ),
        ),
      ).nonAssociations,
    )
  }
}
