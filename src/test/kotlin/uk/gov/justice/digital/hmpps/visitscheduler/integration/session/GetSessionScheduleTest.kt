package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_SESSION_SCHEDULE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionScheduleDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionTemplateFrequency
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@DisplayName("Get $GET_SESSION_SCHEDULE")
class GetSessionScheduleTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private val prisonCode = "MDI"

  @BeforeEach
  internal fun setUpTests() {
  }

  @Test
  fun `Session schedule is returned for a prison`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionLocationGroup = sessionLocationGroupHelper.create(prisonCode = prisonCode)

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedSessionGroups = mutableListOf(sessionLocationGroup),
      enhanced = true,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionScheduleResults[0].endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateReference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateFrequency).isEqualTo(SessionTemplateFrequency.WEEKLY)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateEndDate).isEqualTo(sessionTemplate.validToDate)
    Assertions.assertThat(sessionScheduleResults[0].capacity.open).isEqualTo(sessionTemplate.openCapacity)
    Assertions.assertThat(sessionScheduleResults[0].capacity.closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[0]).isEqualTo(sessionLocationGroup.name)
    Assertions.assertThat(sessionScheduleResults[0].enhanced).isTrue
  }

  @Test
  fun `All session schedules are returned for a prison for that day`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      dayOfWeek = sessionDate.dayOfWeek,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(2)
    Assertions.assertThat(sessionScheduleResults[0].startTime).isEqualTo(sessionTemplate1.startTime)
    Assertions.assertThat(sessionScheduleResults[0].endTime).isEqualTo(sessionTemplate1.endTime)
    Assertions.assertThat(sessionScheduleResults[1].startTime).isEqualTo(sessionTemplate2.startTime)
    Assertions.assertThat(sessionScheduleResults[1].endTime).isEqualTo(sessionTemplate2.endTime)
  }

  @Test
  fun `When no schedules none are returned`() {
    // Given
    val sessionDate = LocalDate.now()

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(0)
  }

  @Test
  fun `One off session schedule are returned for a schedules that only have one session between valid from to valid to dates`() {
    // Given
    val nextMonday = LocalDate.now().with(TemporalAdjusters.next(MONDAY))
    val firstSessionDate = nextMonday.with(TemporalAdjusters.next(TUESDAY))
    val nextMondayAfterFirstSessionDate = firstSessionDate.with(TemporalAdjusters.next(MONDAY))

    sessionTemplateEntityHelper.create(
      validFromDate = nextMonday,
      validToDate = nextMondayAfterFirstSessionDate,
      dayOfWeek = firstSessionDate.dayOfWeek,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, firstSessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateFrequency).isEqualTo(SessionTemplateFrequency.ONE_OFF)
  }

  @Test
  fun `BiWeekly session schedule are returned for a prison`() {
    // Given
    val sessionDate = LocalDate.now()

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusMonths(4),
      dayOfWeek = sessionDate.dayOfWeek,
      biWeekly = true,
    )
    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateFrequency).isEqualTo(SessionTemplateFrequency.BI_WEEKLY)
  }

  @Test
  fun `BiWeekly session schedule are not returned for a prison when BiWeekly not for this week`() {
    // Given
    val sessionDate = LocalDate.now()

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate.minusWeeks(1),
      validToDate = sessionDate.plusMonths(4),
      dayOfWeek = sessionDate.dayOfWeek,
      biWeekly = true,
    )
    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(0)
  }

  @Test
  fun `BiWeekly configured session schedule are returned for a prison as one off if not more than a week`() {
    // Given
    val sessionDate = LocalDate.now()

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate,
      dayOfWeek = sessionDate.dayOfWeek,
      biWeekly = true,
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].sessionTemplateFrequency).isEqualTo(SessionTemplateFrequency.ONE_OFF)
  }

  @Test
  fun `Session schedule is returned for a prison, with many location groups`() {
    // Given
    val sessionDate = LocalDate.now()

    val sessionLocationGroup1 = sessionLocationGroupHelper.create(prisonCode = prisonCode)
    val sessionLocationGroup2 = sessionLocationGroupHelper.create(name = "group B", prisonCode = prisonCode)

    sessionTemplateEntityHelper.create(
      validFromDate = sessionDate,
      validToDate = sessionDate.plusDays(7),
      dayOfWeek = sessionDate.dayOfWeek,
      permittedSessionGroups = mutableListOf(sessionLocationGroup1, sessionLocationGroup2),
    )

    // When
    val responseSpec = callGetSessionSchedule(prisonCode, sessionDate)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionScheduleResults = getResults(returnResult)
    Assertions.assertThat(sessionScheduleResults.size).isEqualTo(1)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[0]).isEqualTo(sessionLocationGroup1.name)
    Assertions.assertThat(sessionScheduleResults[0].prisonerLocationGroupNames[1]).isEqualTo(sessionLocationGroup2.name)
  }

  private fun callGetSessionSchedule(
    prisonCode: String = "MDI",
    scheduleDate: LocalDate,
  ): ResponseSpec {
    return webTestClient.get().uri("$GET_SESSION_SCHEDULE?prisonId=$prisonCode&date=$scheduleDate")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getResults(returnResult: BodyContentSpec): Array<SessionScheduleDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<SessionScheduleDto>::class.java)
  }
}
