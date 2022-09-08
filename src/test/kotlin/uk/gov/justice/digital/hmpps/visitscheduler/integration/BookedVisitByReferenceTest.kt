package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@DisplayName("GET /visits/booked/{reference}")
class BookedVisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @BeforeEach
  internal fun createVisits() {
    visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStart(visitTime)
      .withPrisonId("MDI")
      .save()
  }

  @Test
  fun `get booked visit by reference`() {

    // Given
    val createdVisit = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStatus(BOOKED)
      .withVisitStart(visitTime)
      .save()

    // When
    val responseSpec = callVisitEndPoint("/visits/booked/${createdVisit.reference}")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(createdVisit.reference)
  }

  @Test
  fun `get booked visit by reference - not found`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = callVisitEndPoint("/visits/booked/$reference")

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `get not booked visit by reference`() {

    // Given
    val createdVisit = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStatus(CANCELLED)
      .withVisitStart(visitTime)
      .save()

    // When
    val responseSpec = callVisitEndPoint("/visits/booked/${createdVisit.reference}")

    // Then
    responseSpec.expectStatus().isNotFound
  }

  fun callVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
