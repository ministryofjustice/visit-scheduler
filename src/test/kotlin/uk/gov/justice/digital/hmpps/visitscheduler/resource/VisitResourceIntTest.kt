package uk.gov.justice.digital.hmpps.visitscheduler.resource

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import java.time.LocalDateTime

class VisitResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
    val testPrisonerId: String = "FF12345F"
  }

  @DisplayName("GET /visits/prisoner/{prisonerId}")
  @Nested
  inner class GetVisitsByPrisoner {
    @BeforeEach
    internal fun createVisits() {
      visitCreator(visitRepository)
        .withPrisonerId(testPrisonerId)
        .withVisitDateTime(visitTime)
        .buildAndSave()
    }

    @AfterEach
    internal fun deleteAllVisits() = visitDeleter(visitRepository)

    @Test
    fun `get visit by prisoner ID`() {

      webTestClient.get().uri("/visits/prisoner/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf("ROLE_PLACEHOLDER_VISIT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[-1].prisonerId").isEqualTo(testPrisonerId)
        .jsonPath("$[-1].visitDateTime").isEqualTo(visitTime.toString())
        .jsonPath("$[-1].id").exists()
    }

    @Test
    fun `access forbidden when no role`() {

      webTestClient.get().uri("/visits/prisoner/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `get visit by prisoner ID forbidden with wrong role`() {

      webTestClient.get().uri("/visits/prisoner/$testPrisonerId")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `no visits found for prisoner`() {

      webTestClient.get().uri("/visits/prisoner/12345")
        .headers(setAuthorisation(roles = listOf("ROLE_PLACEHOLDER_VISIT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0)
    }
  }
}
