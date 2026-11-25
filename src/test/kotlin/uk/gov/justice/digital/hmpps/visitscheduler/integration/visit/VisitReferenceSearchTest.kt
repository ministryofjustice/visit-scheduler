package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REFERENCE_SEARCH_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
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
      .jsonPath("$.page.number").isEqualTo(0)
      .jsonPath("$.page.size").isEqualTo(2)
      .jsonPath("$.page.totalElements").isEqualTo(3)
      .jsonPath("$.page.totalPages").isEqualTo(2)
      .jsonPath("$.content[0]").isEqualTo(third.reference)
      .jsonPath("$.content[1]").isEqualTo(second.reference)

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
  fun `includes both visit types by default and filters by either type when supplied`() {
    val social = visitEntityHelper.create(sessionTemplate = sessionTemplateDefault, slotDate = startDate)
    val video = visitEntityHelper.create(sessionTemplate = sessionTemplateDefault, slotDate = startDate, visitType = VisitType.SOCIAL_VIDEO)

    search(dateRange)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0]").isEqualTo(video.reference)
      .jsonPath("$.content[1]").isEqualTo(social.reference)

    search("$dateRange&visitType=SOCIAL")
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0]").isEqualTo(social.reference)

    search("$dateRange&visitType=SOCIAL_VIDEO")
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0]").isEqualTo(video.reference)
  }

  @Test
  fun `returns an empty page when there are no matches`() {
    search(dateRange)
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content").isEmpty
      .jsonPath("$.page.totalElements").isEqualTo(0)
      .jsonPath("$.page.totalPages").isEqualTo(0)
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
      "fromDate=2026-11-01&toDate=2026-11-30&visitType=INVALID",
    ],
  )
  fun `rejects invalid search parameters`(params: String) {
    search(params).expectStatus().isBadRequest
  }

  @Test
  fun `rejects an authenticated caller with no role`() {
    search(dateRange, roles = emptyList()).expectStatus().isForbidden
  }

  @Test
  fun `rejects a caller with only the general visit scheduler role`() {
    search(dateRange, roles = listOf("ROLE_VISIT_SCHEDULER")).expectStatus().isForbidden
  }

  @Test
  fun `requires authentication`() {
    webTestClient.get().uri("$VISIT_REFERENCE_SEARCH_PATH?$dateRange")
      .exchange()
      .expectStatus().isUnauthorized
  }

  private val dateRange get() = "fromDate=$startDate&toDate=$startDate"

  private fun search(params: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER__VISIT_REFERENCE_SEARCH")) = webTestClient.get()
    .uri("$VISIT_REFERENCE_SEARCH_PATH?$params")
    .headers(setAuthorisation(roles = roles))
    .exchange()
}
