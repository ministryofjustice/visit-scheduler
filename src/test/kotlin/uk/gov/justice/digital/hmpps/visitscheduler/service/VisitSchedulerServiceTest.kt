package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitSession
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class VisitSchedulerServiceTest {

  companion object;

  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val visitRepository = mock<VisitRepository>()

  private lateinit var visitSchedulerService: VisitSchedulerService

  private val clock =
    Clock.fixed(Instant.parse("2021-01-01T11:15:00.00Z"), ZoneId.systemDefault()) // today is Friday Jan 1st

  @BeforeEach
  fun setUp() {
    visitSchedulerService = VisitSchedulerService(
      visitRepository,
      sessionTemplateRepository,
      clock,
      100,
      1
    )
  }

  @Nested
  @DisplayName("simple session generation")
  inner class SlotGeneration {

    private fun mockRepositoryResponse(response: List<SessionTemplate>) {
      Mockito.`when`(
        sessionTemplateRepository.findValidSessionTemplatesByPrisonId(
          "MDI",
          LocalDate.parse("2021-01-01").plusDays(1),
          LocalDate.parse("2021-01-01").plusDays(100)
        )
      ).thenReturn(response)
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
      mockRepositoryResponse(listOf(dailySession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(7) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp).containsExactly(
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
      mockRepositoryResponse(listOf(weeklySession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp).containsExactly(
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
      mockRepositoryResponse(listOf(weeklySession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5) // expiry date is inclusive
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp).containsExactly(
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
      mockRepositoryResponse(listOf(singleSession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(1)
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp).containsExactly(
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
      mockRepositoryResponse(listOf(dailySession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
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

      mockRepositoryResponse(listOf(monthlySession, dailySession))

      val sessions = visitSchedulerService.getVisitSessions("MDI")
      assertThat(sessions).size().isEqualTo(5)
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp)
        .containsExactly( // ordered by start date time
          LocalDateTime.parse("2021-01-02T16:00"),
          LocalDateTime.parse("2021-01-03T16:00"),
          LocalDateTime.parse("2021-01-04T16:00"),
          LocalDateTime.parse("2021-01-05T16:00"),
          LocalDateTime.parse("2021-02-01T11:30")
        )
      assertThat(sessions).extracting<LocalDateTime>(VisitSession::startTimestamp)
        .containsExactly( // ordered by start date time
          LocalDateTime.parse("2021-01-02T16:00"),
          LocalDateTime.parse("2021-01-03T16:00"),
          LocalDateTime.parse("2021-01-04T16:00"),
          LocalDateTime.parse("2021-01-05T16:00"),
          LocalDateTime.parse("2021-02-01T11:30")
        )
    }
  }
}
