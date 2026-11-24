package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REFERENCE_SEARCH_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase

@DisplayName("GET /visits/search/references")
class VisitReferenceSearchTest : IntegrationTestBase() {
  @Test
  fun `returns only references and pages consistently across the inclusive date range`() {
    val first = createApplicationAndVisit(slotDate = startDate)
    val second = createApplicationAndVisit(slotDate = startDate.plusDays(1))
    val third = createApplicationAndVisit(slotDate = startDate.plusDays(1))
    createApplicationAndVisit(slotDate = startDate.minusDays(1))
    createApplicationAndVisit(slotDate = startDate.plusDays(2))
    val dates = "fromDate=$startDate&toDate=${startDate.plusDays(1)}"

    search("$dates&size=2")
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0]").isEqualTo(third.reference)
      .jsonPath("$.content[1]").isEqualTo(second.reference)
      .jsonPath("$.totalElements").isEqualTo(3)
      .jsonPath("$.totalPages").isEqualTo(2)

    search("$dates&page=1&size=2")
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0]").isEqualTo(first.reference)
  }

  @Test
  fun `combines prisoner prison and visit status filters`() {
    val otherPrison = sessionTemplateEntityHelper.create(prisonCode = "AWE")
    val prisonerId = "A1234AA"
    val matching = createApplicationAndVisit(prisonerId = prisonerId, slotDate = startDate, sessionTemplate = sessionTemplateDefault)
    createApplicationAndVisit(prisonerId = prisonerId, slotDate = startDate, sessionTemplate = otherPrison)
    createApplicationAndVisit(prisonerId = "B1234BB", slotDate = startDate, sessionTemplate = sessionTemplateDefault)
    createApplicationAndVisit(
      prisonerId = prisonerId,
      slotDate = startDate,
      sessionTemplate = sessionTemplateDefault,
      visitStatus = VisitStatus.CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
    )

    search("$dateRange&prisonerId=$prisonerId&prisonId=${sessionTemplateDefault.prison.code}&visitStatus=BOOKED")
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0]").isEqualTo(matching.reference)
  }

  @Test
  fun `returns an empty page when there are no matches`() {
    search(dateRange)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isEmpty
      .jsonPath("$.totalElements").isEqualTo(0)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "fromDate=2026-11-01",
      "toDate=2026-11-30",
      "fromDate=not-a-date&toDate=2026-11-30",
      "fromDate=2026-11-30&toDate=2026-11-01",
      "fromDate=2026-11-01&toDate=2026-11-30&page=-1",
      "fromDate=2026-11-01&toDate=2026-11-30&size=0",
      "fromDate=2026-11-01&toDate=2026-11-30&size=10001",
      "fromDate=2026-11-01&toDate=2026-11-30&visitStatus=INVALID",
    ],
  )
  fun `rejects invalid search parameters`(params: String) {
    search(params).expectStatus().isBadRequest
  }

  @Test
  fun `requires the visit scheduler role`() {
    search(dateRange, roles = emptyList()).expectStatus().isForbidden
  }

  @Test
  fun `requires authentication`() {
    webTestClient.get().uri("$VISIT_REFERENCE_SEARCH_PATH?$dateRange")
      .exchange()
      .expectStatus().isUnauthorized
  }

  private val dateRange get() = "fromDate=$startDate&toDate=$startDate"

  private fun search(params: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")) = webTestClient.get()
    .uri("$VISIT_REFERENCE_SEARCH_PATH?$params")
    .headers(setAuthorisation(roles = roles))
    .exchange()
}
