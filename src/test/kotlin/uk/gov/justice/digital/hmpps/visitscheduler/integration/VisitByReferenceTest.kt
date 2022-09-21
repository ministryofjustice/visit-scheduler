package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `get visit by reference`() {

    // Given
    val createdVisit = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStatus(BOOKED)
      .withVisitStart(visitTime)
      .save()

    // When
    val responseSpec = callVisitEndPoint(createdVisit.reference)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(createdVisit.reference)
  }

  @Test
  fun `get visit by reference - not found`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = callVisitEndPoint(reference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  fun callVisitEndPoint(reference: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(getUrl(reference))
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }

  private fun getUrl(reference: String): String {
    return GET_VISIT_BY_REFERENCE.replace("{reference}", reference)
  }
}
