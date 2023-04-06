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
    Assertions.assertThat(sessionCapacity[0].closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionCapacity[0].open).isEqualTo(sessionTemplate.openCapacity)
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
      biWeekly = true,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay.plusWeeks(1),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      openCapacity = 0,
      closedCapacity = 10,
      biWeekly = true,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity[0].closed).isEqualTo(sessionTemplate1.closedCapacity)
    Assertions.assertThat(sessionCapacity[0].open).isEqualTo(sessionTemplate1.openCapacity)
  }

  @Test
  fun `Capacity groups capacities are added up`() {
    // Given

    val nextAllowedDay = getNextAllowedDay()

    val sessionTemplate1 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      capacityGroup = "G1",
      closedCapacity = 1,
      openCapacity = 1,
    )

    val sessionTemplate2 = sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      capacityGroup = "G2",
      closedCapacity = 10,
      openCapacity = 11,
    )

    sessionTemplateEntityHelper.create(
      validFromDate = nextAllowedDay,
      validToDate = nextAllowedDay,
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      dayOfWeek = nextAllowedDay.dayOfWeek,
      capacityGroup = "G2",
      closedCapacity = sessionTemplate2.closedCapacity,
      openCapacity = sessionTemplate2.openCapacity,
    )

    // When
    val responseSpec = callGetSessionCapacity(sessionTemplate1.prison.code, sessionTemplate1.validFromDate, sessionTemplate1.startTime, sessionTemplate1.endTime)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val sessionCapacity = getResults(returnResult)
    Assertions.assertThat(sessionCapacity.size).isEqualTo(2)
    Assertions.assertThat(sessionCapacity[0].closed).isEqualTo(sessionTemplate1.closedCapacity)
    Assertions.assertThat(sessionCapacity[0].open).isEqualTo(sessionTemplate1.openCapacity)
    Assertions.assertThat(sessionCapacity[1].closed).isEqualTo(sessionTemplate2.closedCapacity * 2)
    Assertions.assertThat(sessionCapacity[1].open).isEqualTo(sessionTemplate2.openCapacity * 2)
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
    prisonCode: String ? = "MDI",
    sessionDate: LocalDate ? = LocalDate.parse("2023-01-26"),
    sessionStartTime: LocalTime ? = LocalTime.parse("13:45"),
    sessionEndTime: LocalTime ? = LocalTime.parse("14:45"),
  ): ResponseSpec {
    return webTestClient.get().uri("$GET_SESSION_CAPACITY?prisonId=$prisonCode&sessionDate=$sessionDate&sessionStartTime=$sessionStartTime&sessionEndTime=$sessionEndTime")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()
  }

  private fun getNextAllowedDay(): LocalDate {
    // The two days is based on the default SessionService.policyNoticeDaysMin
    return LocalDate.now().plusDays(2)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<SessionCapacityDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<SessionCapacityDto>::class.java)
  }
}
