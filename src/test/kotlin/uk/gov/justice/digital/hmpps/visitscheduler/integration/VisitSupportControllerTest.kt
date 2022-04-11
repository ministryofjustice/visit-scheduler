package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.visitscheduler.test_setup.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.test_setup.integration.IntegrationTestBase

@Import(TestClockConfiguration::class)
class VisitSupportControllerTest : IntegrationTestBase() {

  @DisplayName("Get /visit-support")
  @Nested
  inner class GetSupport {

    @Test
    fun `all available support is returned`() {
      webTestClient.get().uri("/visit-support")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(5)
    }
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.get().uri("/visit-support")
      .headers(setAuthorisation(roles = listOf()))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    webTestClient.get().uri("/visit-support")
      .exchange()
      .expectStatus().isUnauthorized
  }
}
