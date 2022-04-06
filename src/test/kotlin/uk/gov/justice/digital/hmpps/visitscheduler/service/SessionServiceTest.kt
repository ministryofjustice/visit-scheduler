package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OffenderNonAssociationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class SessionServiceTest {

  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val prisonApiClient = mock<PrisonApiClient>()

  private lateinit var sessionService: SessionService

  private val clock =
    Clock.fixed(Instant.parse("2021-01-01T11:15:00.00Z"), ZoneId.systemDefault()) // today is Friday Jan 1st

  @BeforeEach
  fun setUp() {
    sessionService = SessionService(
      sessionTemplateRepository,
      visitRepository,
      prisonApiClient,
      clock,
      1,
      100,
      true
    )
  }

  @Nested
  @DisplayName("simple session generation")
  inner class SlotGeneration {

    private fun mockSessionRepositoryResponse(response: List<SessionTemplate>) {
      whenever(
        sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
          "MDI",
          LocalDate.parse("2021-01-01").plusDays(1),
          LocalDate.parse("2021-01-01").plusDays(100)
        )
      ).thenReturn(response)
    }

    private fun mockVisitRepositoryResponse(response: List<Visit>) {
      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(response)
    }

    @Test
    fun `a daily session will return 7 sessions including first bookable day and expiry day`() {
      val dailySession = sessionTemplate(
        expiryDate = LocalDate.parse("2021-01-08"),
        startDate = LocalDate.parse("2021-01-01"),
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
        frequency = SessionFrequency.DAILY
      )
      mockSessionRepositoryResponse(listOf(dailySession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(7) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-01-02T11:30:00"),
        LocalDateTime.parse("2021-01-03T11:30:00"),
        LocalDateTime.parse("2021-01-04T11:30:00"),
        LocalDateTime.parse("2021-01-05T11:30:00"),
        LocalDateTime.parse("2021-01-06T11:30:00"),
        LocalDateTime.parse("2021-01-07T11:30:00"),
        LocalDateTime.parse("2021-01-08T11:30:00"),
      )
    }

    @Test
    fun `a weekly session will return 6 sessions including today and expiry date`() {
      val weeklySession = sessionTemplate(
        expiryDate = LocalDate.parse("2021-01-01").plusWeeks(5),
        startDate = LocalDate.parse("2021-01-01"),
        closedCapacity = 5,
        openCapacity = 10,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.WEEKLY
      )
      mockSessionRepositoryResponse(listOf(weeklySession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-01-08T11:30:00"),
        LocalDateTime.parse("2021-01-15T11:30:00"),
        LocalDateTime.parse("2021-01-22T11:30:00"),
        LocalDateTime.parse("2021-01-29T11:30:00"),
        LocalDateTime.parse("2021-02-05T11:30:00"),
      )
    }

    @Test
    fun `sessions are consistently generated, weekly sessions always fall on the same day regardless of date of generation`() {
      val weeklySession = sessionTemplate(
        expiryDate = LocalDate.parse("2021-01-01").plusWeeks(5), // 5 weeks from today
        startDate = LocalDate.parse("2020-12-30"), // session template start date is a Wednesday
        closedCapacity = 5,
        openCapacity = 10,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.WEEKLY
      )
      mockSessionRepositoryResponse(listOf(weeklySession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-01-06T11:30:00"), // first wednesday after today (1/1/2021)
        LocalDateTime.parse("2021-01-13T11:30:00"),
        LocalDateTime.parse("2021-01-20T11:30:00"),
        LocalDateTime.parse("2021-01-27T11:30:00"),
        LocalDateTime.parse("2021-02-03T11:30:00"),
      )
    }

    @Test
    fun `a single session will return 1 session`() {
      val singleSession = sessionTemplate(
        startDate = LocalDate.parse("2021-02-01"),
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
        frequency = SessionFrequency.SINGLE
      )
      mockSessionRepositoryResponse(listOf(singleSession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-02-01T11:30:00")
      )
    }

    @Test
    fun `all sessions are on past dates, no sessions are returned`() {
      val dailySession = sessionTemplate(
        startDate = LocalDate.parse("2021-01-01").minusDays(8),
        expiryDate = LocalDate.parse("2021-01-01").minusDays(1),
        frequency = SessionFrequency.DAILY
      )
      mockSessionRepositoryResponse(listOf(dailySession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(0)
    }

    @Test
    fun getMultipleSessions() {
      val monthlySession = sessionTemplate(
        startDate = LocalDate.parse("2021-01-01"),
        expiryDate = LocalDate.parse("2021-02-01"),
        frequency = SessionFrequency.MONTHLY,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("11:45"),
        id = 1
      )
      val dailySession = sessionTemplate(
        startDate = LocalDate.parse("2021-01-01"),
        expiryDate = LocalDate.parse("2021-01-05"),
        frequency = SessionFrequency.DAILY,
        startTime = LocalTime.of(16, 0, 0),
        endTime = LocalTime.of(16, 30, 0),
        id = 2
      )

      mockSessionRepositoryResponse(listOf(monthlySession, dailySession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5)
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp)
        .containsExactly( // ordered by start date time
          LocalDateTime.parse("2021-01-02T16:00"),
          LocalDateTime.parse("2021-01-03T16:00"),
          LocalDateTime.parse("2021-01-04T16:00"),
          LocalDateTime.parse("2021-01-05T16:00"),
          LocalDateTime.parse("2021-02-01T11:30")
        )
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp)
        .containsExactly( // ordered by start date time
          LocalDateTime.parse("2021-01-02T16:00"),
          LocalDateTime.parse("2021-01-03T16:00"),
          LocalDateTime.parse("2021-01-04T16:00"),
          LocalDateTime.parse("2021-01-05T16:00"),
          LocalDateTime.parse("2021-02-01T11:30")
        )
    }

    @Test
    fun `Single Session without Visit has zero Open and zero Closed slot count`() {
      val singleSession = sessionTemplate(
        startDate = LocalDate.parse("2021-02-01"),
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
        frequency = SessionFrequency.SINGLE
      )
      mockSessionRepositoryResponse(listOf(singleSession))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(0)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(0)
    }

    @Test
    fun `Single Session with BOOKED Visit has booked slot count`() {
      val singleSession = sessionTemplate(
        startDate = LocalDate.parse("2021-02-01"),
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
        frequency = SessionFrequency.SINGLE
      )
      mockSessionRepositoryResponse(listOf(singleSession))

      val visit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = LocalDate.parse("2021-02-01").atTime(11, 30),
        visitEnd = LocalDate.parse("2021-02-01").atTime(12, 30),
        visitType = SOCIAL,
        prisonId = "MDI",
        visitStatus = BOOKED,
        visitRestriction = OPEN,
        visitRoom = "123c",
      )
      mockVisitRepositoryResponse(listOf(visit))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }

    @Test
    fun `Single Session with RESERVED Visit has booked slot count`() {
      val singleSession = sessionTemplate(
        startDate = LocalDate.parse("2021-02-01"),
        startTime = LocalTime.parse("11:30"), // future time
        endTime = LocalTime.parse("12:30"), // future time
        frequency = SessionFrequency.SINGLE
      )
      mockSessionRepositoryResponse(listOf(singleSession))

      val visit = Visit(
        prisonerId = "Anythingwilldo",
        visitStart = LocalDate.parse("2021-02-01").atTime(11, 30),
        visitEnd = LocalDate.parse("2021-02-01").atTime(12, 30),
        visitType = SOCIAL,
        prisonId = "MDI",
        visitStatus = RESERVED,
        visitRestriction = OPEN,
        visitRoom = "123c",
      )
      mockVisitRepositoryResponse(listOf(visit))

      val sessions = sessionService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions[0].openVisitBookedCount).isEqualTo(1)
      assertThat(sessions[0].closedVisitBookedCount).isEqualTo(1)
    }
  }

  @Nested
  @DisplayName("Available slots including non-association")
  inner class NonAssociations {

    private fun mockRepositoryResponse(response: List<SessionTemplate>) {
      whenever(
        sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
          "MDI",
          LocalDate.parse("2021-01-01").plusDays(1),
          LocalDate.parse("2021-01-01").plusDays(100)
        )
      ).thenReturn(response)
    }

    @Test
    fun `all sessions are returned when an offender has no non-associations`() {
      val prisonId = "MDI"
      val prisonerId = "A1234AA"
      val startDate = LocalDate.parse("2021-02-01")

      val singleSession = sessionTemplate(
        startDate = startDate,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.SINGLE
      )
      mockRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(OffenderNonAssociationDetailsDto())

      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-02-01T11:30:00")
      )

      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association without a booking`() {
      val prisonId = "MDI"
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"
      val startDate = LocalDate.parse("2021-01-01")

      val singleSession = sessionTemplate(
        startDate = startDate.plusDays(2),
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.SINGLE
      )
      mockRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = startDate.minusMonths(1),
              expiryDate = startDate.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.findAll(any(VisitSpecification::class.java))).thenReturn(emptyList())

      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)
      assertThat(sessions).size().isEqualTo(1)

      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `only available sessions are returned when an offender has a valid non-association with a booking`() {
      val prisonId = "MDI"
      val prisonerId = "A1234AA"
      val associationId = "B1234BB"
      val startDate = LocalDate.parse("2021-01-01")

      val singleSession = sessionTemplate(
        startDate = startDate.plusDays(2),
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.SINGLE
      )
      mockRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenReturn(
        OffenderNonAssociationDetailsDto(
          listOf(
            OffenderNonAssociationDetailDto(
              effectiveDate = startDate.minusMonths(1),
              expiryDate = startDate.plusMonths(1),
              offenderNonAssociation = OffenderNonAssociationDto(offenderNo = associationId)
            )
          )
        )
      )

      whenever(visitRepository.findAll(any(VisitSpecification::class.java)))
        .thenReturn(
          listOf(
            Visit(
              prisonerId = associationId,
              visitStart = startDate.plusDays(2).atTime(10, 30),
              visitEnd = startDate.plusDays(2).atTime(11, 30),
              visitType = SOCIAL,
              prisonId = prisonId,
              visitStatus = BOOKED,
              visitRestriction = OPEN,
              visitRoom = "123c",
            )
          )
        )

      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)
      assertThat(sessions).size().isEqualTo(0)

      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `all sessions are returned when an offender non-association NOT FOUND`() {
      val prisonId = "MDI"
      val prisonerId = "A1234AA"
      val startDate = LocalDate.parse("2021-02-01")

      val singleSession = sessionTemplate(
        startDate = startDate,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.SINGLE
      )
      mockRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.NOT_FOUND.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      val sessions = sessionService.getVisitSessions(prisonId, prisonerId)
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions).extracting<LocalDateTime>(VisitSessionDto::startTimestamp).containsExactly(
        LocalDateTime.parse("2021-02-01T11:30:00")
      )

      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }

    @Test
    fun `get sessions throws WebClientResponseException for BAD REQUEST`() {
      val prisonId = "MDI"
      val prisonerId = "A1234AA"
      val startDate = LocalDate.parse("2021-02-01")

      val singleSession = sessionTemplate(
        startDate = startDate,
        startTime = LocalTime.parse("11:30"),
        endTime = LocalTime.parse("12:30"),
        frequency = SessionFrequency.SINGLE
      )
      mockRepositoryResponse(listOf(singleSession))

      whenever(
        prisonApiClient.getOffenderNonAssociation(prisonerId)
      ).thenThrow(
        WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "", HttpHeaders.EMPTY, byteArrayOf(), null)
      )

      assertThrows<WebClientResponseException> {
        sessionService.getVisitSessions(prisonId, prisonerId)
      }

      Mockito.verify(prisonApiClient, times(1)).getOffenderNonAssociation(prisonerId)
    }
  }
}
