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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.projection.SpelAwareProxyProjectionFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionConflict
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
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
  private val prisonApiClient = mock<PrisonApiClient>()
  private val visitService = mock<VisitService>()

  private lateinit var sessionService: SessionService

  private val date = LocalDate.now()

  private val prisonCode = "MDI"
  private val noticeDaysMin = 1L
  private val noticeDaysMax = 100L

  private fun mockSessionTemplateRepositoryResponse(response: List<SessionTemplate>) {
    whenever(
      sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
        prisonCode,
        date.plusDays(noticeDaysMin),
        date.plusDays(noticeDaysMax)
      )
    ).thenReturn(response)
  }

  private fun mockVisitRepositoryCountResponse(visits: List<Visit>, sessionTemplate: SessionTemplate) {
    val startDateTime = date.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek)).atTime(sessionTemplate.startTime)
    val endDateTime = date.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek)).atTime(sessionTemplate.endTime)

    whenever(
      visitRepository.getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
        sessionTemplate.prison.code,
        sessionTemplate.visitRoom,
        startDateTime,
        endDateTime
      )
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
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        visitService,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true
      )
    }

    @Test
    fun `a weekly session will return 6 sessions including today and valid to date`() {

      // Given
      val weeklySession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(5),
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = FRIDAY
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      val fridayAfter = date.with(TemporalAdjusters.next(weeklySession.dayOfWeek)).atTime(weeklySession.startTime)

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
        validFromDate = date,
        validToDate = date.plusWeeks(5), // 5 weeks from today
        openCapacity = 10,
        closedCapacity = 5,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = WEDNESDAY
      )
      mockSessionTemplateRepositoryResponse(listOf(weeklySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      val wednesdayAfter = date.with(TemporalAdjusters.next(weeklySession.dayOfWeek)).atTime(weeklySession.startTime)
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
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val mondayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
    }

    @Test
    fun `all sessions are on past dates, no sessions are returned`() {

      // Given
      val dailySession = sessionTemplate(
        validFromDate = date.minusDays(8),
        validToDate = date.minusDays(1),
        dayOfWeek = MONDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(dailySession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(0)
    }

    @Test
    fun `Single Session without Visit has zero Open and zero Closed slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Single Session with BOOKED Visit and OPEN and CLOSED restriction has booked slot count`() {

      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val prison = Prison(
        id = 1,
        code = "HEI",
        active = true
      )

      val openVisit1 = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "1"
      )

      val openVisit2 = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "1"
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = BOOKED,
        visitRestriction = CLOSED,
        visitRoom = "1"
      )
      mockVisitRepositoryCountResponse(listOf(openVisit1, openVisit2, closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(2)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Single Session with RESERVED Visit and OPEN and CLOSED restriction has booked slot count`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      val prison = Prison(
        id = 1,
        code = "HEI",
        active = true
      )

      val openVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = OPEN,
        visitRoom = "1"
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = CLOSED,
        visitRoom = "1"
      )
      mockVisitRepositoryCountResponse(listOf(openVisit, closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Sessions with UNKNOWN restriction Visits has booked slot counts of ZERO`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )

      val prison = Prison(
        id = 1,
        code = "HEI",
        active = true
      )

      val closedVisit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = date.atTime(11, 30),
        visitEnd = date.atTime(12, 30),
        visitType = SOCIAL,
        prisonId = prison.id,
        prison = prison,
        visitStatus = RESERVED,
        visitRestriction = UNKNOWN,
        visitRoom = "1"
      )

      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      mockVisitRepositoryCountResponse(listOf(closedVisit), singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Sessions with no Visits has booked slot counts of ZERO`() {
      // Given
      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30") // future time
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      // no BOOKED or RESERVED visits
      val noVisitsBookedOrReserved = emptyList<Visit>()

      mockVisitRepositoryCountResponse(noVisitsBookedOrReserved, singleSession)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode)

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
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        visitService,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = false,
        policyFilterNonAssociation = false,
        policyNonAssociationWholeDay = true
      )
    }

    @Test
    fun `session does not contain conflicts when an offender has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      val mondayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when an offender has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = FRIDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )
      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(false)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      val fridayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)

      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, fridayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), FRIDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when an offender has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"
      val dayOfWeek = date.plusDays(1).dayOfWeek

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = dayOfWeek,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )
      val expectedAssociations = listOf(associationId)
      val startDateTimeFilter = date.plusDays(1).with(singleSession.dayOfWeek).atStartOfDay()
      val endDateTimeFilter = date.plusDays(1).with(singleSession.dayOfWeek).atTime(LocalTime.MAX)

      whenever(visitRepository.hasActiveVisits(expectedAssociations, prisonCode, startDateTimeFilter, endDateTimeFilter))
        .thenReturn(
          true,
          false
        )

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val saturdayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), dayOfWeek)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.NON_ASSOCIATION)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `sessions contain conflicts when an offender has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        dayOfWeek = SATURDAY,
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      val saturdayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, saturdayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), SATURDAY)
      assertThat(sessions[0].sessionConflicts).size().isEqualTo(1)
      assertThat(sessions[0].sessionConflicts!!.first()).isEqualTo(SessionConflict.DOUBLE_BOOKED)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `session does not contain conflicts when an offender non-association NOT FOUND`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      val mondayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertThat(sessions).size().isEqualTo(1)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      assertThat(sessions[0].sessionConflicts).isEmpty()
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `get sessions throws WebClientResponseException for BAD REQUEST`() {

      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      // When
      assertThrows<WebClientResponseException> {
        sessionService.getVisitSessions(prisonCode, prisonerId)
      }

      // Then
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }
  }

  @Nested
  @DisplayName("Available slots exclude conflicts")
  inner class ExcludeConflicts {

    @BeforeEach
    fun setUp() {
      sessionService = SessionService(
        sessionTemplateRepository,
        visitRepository,
        prisonApiClient,
        visitService,
        policyNoticeDaysMin = noticeDaysMin,
        policyNoticeDaysMax = noticeDaysMax,
        policyFilterDoubleBooking = true,
        policyFilterNonAssociation = true,
        policyNonAssociationWholeDay = true
      )
    }

    @Test
    fun `all sessions are returned when an offender has no non-associations and no double bookings`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      val mondayAfter = date.with(TemporalAdjusters.next(singleSession.dayOfWeek)).atTime(singleSession.startTime)
      assertDate(sessions[0].startTimestamp, mondayAfter.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), MONDAY)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association without bookings`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(false)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(1)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association with a booking`() {
      // Given
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = date.minusMonths(1),
              expiryDate = date.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a double booking`() {
      // Given
      val prisonerId = "A1234AA"

      val singleSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `when multiple sessions prison api get offenders is only called once`() {
      // Given
      val prisonerId = "A1234AA"

      val firstSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(1),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )

      val secondSession = sessionTemplate(
        validFromDate = date,
        validToDate = date.plusWeeks(2),
        dayOfWeek = MONDAY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30")
      )
      mockSessionTemplateRepositoryResponse(listOf(firstSession, secondSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      whenever(visitRepository.hasVisits(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(true)

      // When
      val sessions = sessionService.getVisitSessions(prisonCode, prisonerId)

      // Then
      assertThat(sessions).size().isEqualTo(0)
      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }
  }

  private fun assertDate(localDateTime: LocalDateTime, expectedlyDateTime: String, dayOfWeek: DayOfWeek) {
    assertThat(localDateTime).isEqualTo(LocalDateTime.parse(expectedlyDateTime))
    assertThat(localDateTime.dayOfWeek).isEqualTo(dayOfWeek)
  }
}
