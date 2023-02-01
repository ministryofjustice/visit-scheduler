package uk.gov.justice.digital.hmpps.visitscheduler.integration.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.SUPPORTED_PRISONS
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalDate

@DisplayName("Get $SUPPORTED_PRISONS")
class GetSupportedPrisonsTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @Test
  fun `all supported prisons are returned in correct order`() {
    // Given
    val today = LocalDate.now()

    sessionTemplateEntityHelper.create(validFromDate = today, prisonCode = "AWE")
    sessionTemplateEntityHelper.create(validFromDate = today, validToDate = today, prisonCode = "GRE")
    sessionTemplateEntityHelper.create(validFromDate = today.minusDays(1), prisonCode = "CDE")
    sessionTemplateEntityHelper.create(validFromDate = today.minusDays(1), validToDate = today, prisonCode = "BDE")
    sessionTemplateEntityHelper.create(validFromDate = today.minusDays(2), validToDate = today.plusDays(1), prisonCode = "WDE")

    // When
    val responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getResults(returnResult)

    assertThat(results.size).isEqualTo(5)
    assertThat(results[0]).isEqualTo("AWE")
    assertThat(results[1]).isEqualTo("BDE")
    assertThat(results[2]).isEqualTo("CDE")
    assertThat(results[3]).isEqualTo("GRE")
    assertThat(results[4]).isEqualTo("WDE")
  }

  @Test
  fun `sessions with inactive prisons are not returned`() {
    // Given
    sessionTemplateEntityHelper.create(prisonCode = "GRE", activePrison = false)
    sessionTemplateEntityHelper.create(prisonCode = "CDE", activePrison = false)

    // When
    val responseSpec = webTestClient.get().uri(SUPPORTED_PRISONS)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
    val results = getResults(returnResult)

    assertThat(results.size).isEqualTo(0)
  }

  private fun getResults(returnResult: BodyContentSpec): Array<String> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<String>::class.java)
  }
}
