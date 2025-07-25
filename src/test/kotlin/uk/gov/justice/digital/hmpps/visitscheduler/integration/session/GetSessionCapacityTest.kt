package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_SESSION_CAPACITY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Get $GET_SESSION_CAPACITY")
class GetSessionCapacityTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `get session capacity`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate.prison.code, sessionTemplate.validFromDate, sessionTemplate.startTime, sessionTemplate.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionCapacity.open).isEqualTo(sessionTemplate.openCapacity)
  }

  @Test
  fun `get session capacity for BI Weekly sessions`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      openCapacity = 20,
      closedCapacity = 0,
      weeklyFrequency = 2,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay.plusWeeks(1),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      openCapacity = 0,
      closedCapacity = 10,
      weeklyFrequency = 2,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.closed).isEqualTo(sessionTemplate1.closedCapacity)
    Assertions.assertThat(sessionCapacity.open).isEqualTo(sessionTemplate1.openCapacity)
  }

  @Test
  fun `Capacities are added up`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      visitRoom = "G1",
      closedCapacity = 1,
      openCapacity = 1,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      visitRoom = "G2",
      closedCapacity = 10,
      openCapacity = 11,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      visitRoom = "G2",
      closedCapacity = sessionTemplate2.closedCapacity,
      openCapacity = sessionTemplate2.openCapacity,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.closed).isEqualTo(21)
    Assertions.assertThat(sessionCapacity.open).isEqualTo(23)
  }

  @Test
  fun `Sessions capacity are added up`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      closedCapacity = 1,
      openCapacity = 1,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      closedCapacity = 10,
      openCapacity = 11,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.closed).isEqualTo(sessionTemplate1.closedCapacity + sessionTemplate2.closedCapacity)
    Assertions.assertThat(sessionCapacity.open).isEqualTo(sessionTemplate1.openCapacity + sessionTemplate2.openCapacity)
  }

  @Test
  fun `Sessions capacity ignores inactive session capacity`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      closedCapacity = 1,
      openCapacity = 1,
    )

    // inactive session
    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      closedCapacity = 10,
      openCapacity = 11,
      isActive = false,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.closed).isEqualTo(sessionTemplate1.closedCapacity)
    Assertions.assertThat(sessionCapacity.open).isEqualTo(sessionTemplate1.openCapacity)
  }

  @Test
  fun `throw 404 Not found exception when more than one capacity is found`() {
    // Given

    // When
    val responseSpec = callGetSessionCapacity()

    // Then
    responseSpec.expectStatus().isNotFound
  }

  private fun callGetSessionCapacity(
    prisonCode: String? = "MDI",
    sessionDate: LocalDate? = LocalDate.parse("2023-01-26"),
    sessionStartTime: LocalTime? = LocalTime.parse("13:45"),
    sessionEndTime: LocalTime? = LocalTime.parse("14:45"),
  ): ResponseSpec = webTestClient.get().uri("$GET_SESSION_CAPACITY?prisonId=$prisonCode&sessionDate=$sessionDate&sessionStartTime=$sessionStartTime&sessionEndTime=$sessionEndTime")
    .headers(setAuthorisation(roles = requiredRole))
    .exchange()

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    // VB-5790 - adding 1 day after adding policyNoticeDaysMin as there is a change wherein
    // fix sessions are returned after n whole days and not and not today + n so adding a day
    // e.g if today is WED and policyNoticeDaysMin is 2 sessions need to be returned from SATURDAY and not FRIDAY
    return LocalDate.now().plusDays(2).plusDays(1)
  }

  private fun getResults(returnResult: BodyContentSpec): SessionCapacityDto = objectMapper.readValue(returnResult.returnResult().responseBody, SessionCapacityDto::class.java)
}
