package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.visitscheduler.helper.TestClockConfiguration

@Import(TestClockConfiguration::class)
class VisitSupportControllerTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

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

    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    webTestClient.get().uri("/visit-support")
      .exchange()
      .expectStatus().isUnauthorized
  }
}
