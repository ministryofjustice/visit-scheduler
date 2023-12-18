package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OtherPrisonerDetails
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLocationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException
import uk.gov.justice.digital.hmpps.visitscheduler.helper.PrisonEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerSessionValidator
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
  private val prisonerService = mock<PrisonerService>()
  private val prisonerValidationService = mock<PrisonerValidationService>()
  private val visitService = mock<VisitService>()
  private val prisonerSessionValidator = mock<PrisonerSessionValidator>()
  private val sessionDatesUtil = SessionDatesUtil()
  private val prisonsService = mock<PrisonsService>()

  private lateinit var sessionService: SessionService

  private val currentDate = LocalDate.now()

  private val prisonCode = "MDI"
  private val noticeDaysMin = 1
  private val noticeDaysMax = 100
  private val prisonerId = "AA1234BB"

  @BeforeEach
  fun beforeEachTestSetup() {
    whenever(prisonerService.getPrisoner(any())).thenReturn(PrisonerDto(prisonerId, "C", IncentiveLevel.STANDARD, prisonCode))
    whenever(prisonsService.findPrisonByCode(prisonCode)).thenReturn(PrisonEntityHelper.createPrison(prisonCode, policyNoticeDaysMin = noticeDaysMin, policyNoticeDaysMax = noticeDaysMax))

    whenever(prisonerService.getPrisonerHousingLocation(any(), any())).thenReturn(
      PrisonerHousingLocationsDto(
        levels = listOf(),
      ),
    )

    whenever(prisonerService.getLevelsMapForPrisoner(any())).thenReturn(mutableMapOf<PrisonerHousingLevels, String?>())
    whenever(prisonerSessionValidator.isSessionAvailableToPrisonerLocation(any(), any())).thenReturn(true)
  }

  private fun mockSessionTemplateRepositoryResponse(response: List<SessionTemplate>, incentiveLevel: IncentiveLevel? = null, category: String ? = null) {
    whenever(
      prisonerService.getPrisoner(any()),
    ).thenReturn(PrisonerDto(prisonerId = prisonerId, category = category, incentiveLevel = incentiveLevel))

    whenever(
      sessionTemplateRepository.findSessionTemplateMinCapacityBy(
        prisonCode = prisonCode,
        rangeStartDate = currentDate.plusDays(noticeDaysMin.toLong()),
        rangeEndDate = currentDate.plusDays(noticeDaysMax.toLong()),
      ),
    ).thenReturn(response)
  }

  private fun mockVisitRepositoryCountResponse(visits: List<Visit>, sessionTemplate: SessionTemplate) {
    val startDateTime = currentDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek)).atTime(sessionTemplate.startTime)

    whenever(
      visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
        sessionTemplateReference = sessionTemplate.reference,
        startDateTime.toLocalDate(),
      ),
    ).thenReturn(getVisitRestrictionStatsList(visits))
  }

  private fun getVisitRestrictionStatsList(visits: List<Visit>): List<VisitRestrictionStats> {
    return listOf(getVisitRestrictionStats(visits, OPEN), getVisitRestrictionStats(visits, CLOSED))
  }

  private fun getVisitRestrictionStats(visits: List<Visit>, visitRestriction: VisitRestriction): VisitRestrictionStats {
    val factory: ProjectionFactory = SpelAwareProxyProjectionFactory()
    val backingMap: MutableMap<String, Any> = HashMap()
    backingMap["visitRestriction"] = visitRestriction
    backingMap["count"] = visits.count { it.visitRestriction == visitRestriction }
    return factory.createProjection(VisitRestrictionStats::class.java, backingMap)
  }

  @Nested
  @DisplayName("simple session generation")
  inner class SlotGeneration {
    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionDatesUtil,
        sessionTemplateRepository,
        visitRepository,
        prisonerService,
        visitService,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true,
        sessionValidator = prisonerSessionValidator,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
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
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        validToDate = currentDate.plusWeeks(5), // 5 weeks from today
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = WEDNESDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val prison = PrisonEntityHelper.createPrison()

      val openVisit1 = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "1",
      )

      val openVisit2 = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "1",
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = CLOSED,
        visitRoom = "1",
      )
      mockVisitRepositoryCountResponse(listOf(openVisit1, openVisit2, closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(2)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Single Session with RESERVED Visit and OPEN and CLOSED restriction has booked slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val prison = PrisonEntityHelper.createPrison()

      val openVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = OPEN,
        sessionTemplateReference = singleSession.reference,
        visitRoom = "1",
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = CLOSED,
        sessionTemplateReference = singleSession.reference,
        visitRoom = "1",
      )
      mockVisitRepositoryCountResponse(listOf(openVisit, closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Sessions with UNKNOWN restriction Visits has booked slot counts of ZERO`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = currentDate,
        validToDate = currentDate.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )

      val prison = PrisonEntityHelper.createPrison()

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = currentDate.atTime(11, 30),
        visitEnd = currentDate.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = UNKNOWN,
        visitRoom = "1",
      )

      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockVisitRepositoryCountResponse(listOf(closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // no BOOKED or RESERVED visits
      val noVisitsBookedOrReserved = emptyList<Visit>()

      mockVisitRepositoryCountResponse(noVisitsBookedOrReserved, singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        sessionDatesUtil,
        sessionTemplateRepository,
        visitRepository,
        prisonerService,
        visitService,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true,
        sessionValidator = prisonerSessionValidator,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
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
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      mockGetPrisonerNoAssociation(prisonerId, associationId)

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(false)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      val singleSession = sessionTemplate(
        validFromDate = currentDate.plusDays(noticeDaysMin.toLong()),
        validToDate = validFromDate.plusWeeks(1),
        dayOfWeek = dayOfWeek,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))
      mockGetPrisonerNoAssociation(prisonerId, associationId)

      val expectedAssociations = listOf(associationId)
      val startDateTimeFilter = validFromDate.plusDays(1).with(singleSession.dayOfWeek).atStartOfDay()
      val endDateTimeFilter = validFromDate.plusDays(1).with(singleSession.dayOfWeek).atTime(LocalTime.MAX)

      whenever(visitRepository.hasActiveVisits(expectedAssociations, prisonCode, startDateTimeFilter, endDateTimeFilter))
        .thenReturn(
          true,
          false,
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val saturdayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dayOfWeek)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.NON_ASSOCIATION)
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

      whenever(
        prisonerService.getPrisonerNonAssociationList(prisonerId),
      ).thenReturn(PrisonerNonAssociationDetailsDto().nonAssociations)

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      val saturdayAfter = currentDate.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dayOfWeek)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.DOUBLE_BOOKED)
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
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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
        sessionService.getVisitSessions(prisonCode, prisonerId)
      }

      // Then
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }

    @Test
    fun `when prisonId and prisonerId do not match get sessions throws PrisonerNotInSuppliedPrisonException`() {
      // Given
      val prisonerId = "A1234AA"
      val incorrectPrisonCode = "ABC"
      val prisonerDto = PrisonerDto(prisonerId = prisonerId, prisonCode = incorrectPrisonCode)

      whenever(
        prisonerService.getPrisoner(prisonerId),
      ).thenReturn(prisonerDto)

      whenever(
        prisonerValidationService.validatePrisonerIsFromPrison(prisonerDto, prisonCode),
      ).thenCallRealMethod()

      // When
      // the prison code being passed is not the same as the prisoners details on Prison API
      assertThrows<PrisonerNotInSuppliedPrisonException> {
        sessionService.getVisitSessions(prisonCode, prisonerId)
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
        sessionDatesUtil,
        sessionTemplateRepository,
        visitRepository,
        prisonerService,
        visitService,
        policyFilterDoubleBooking = true,
        policyFilterNonAssociation = true,
        policyNonAssociationWholeDay = true,
        sessionValidator = prisonerSessionValidator,
        prisonerValidationService = prisonerValidationService,
        prisonsService = prisonsService,
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

      mockGetPrisonerNoAssociation(prisonerId, "associationID")

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      mockGetPrisonerNoAssociation(prisonerId, associationId)

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(false)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      mockGetPrisonerNoAssociation(prisonerId, associationId)

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      mockGetPrisonerNoAssociation(prisonerId, "associationID")

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

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

      mockGetPrisonerNoAssociation(prisonerId, "associationID")

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonerService, times(1)).getPrisonerNonAssociationList(prisonerId)
    }
  }

  private fun assertDate(localDateTime: LocalDateTime, expectedlyDateTime: String, dayOfWeek: DayOfWeek) {
    assertThat(localDateTime).isEqualTo(LocalDateTime.parse(expectedlyDateTime))
    assertThat(localDateTime.dayOfWeek).isEqualTo(dayOfWeek)
  }

  private fun mockGetPrisonerNoAssociation(prisonerId: String, associationId: String) {
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
