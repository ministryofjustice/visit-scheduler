package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean

@DisplayName("Get /visit-support")
class VisitSupportControllerTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun `all available support is returned`() {
    // Give
    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.get().uri("/visit-support")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(5)
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val emptyRoles: List<String> = emptyList()

    // When

    val responseSpec = webTestClient.get().uri("/visit-support")
      .headers(setAuthorisation(roles = emptyRoles))
      .exchange()

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    // When
    val responseSpec = webTestClient.get().uri("/visit-support")
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }
}
