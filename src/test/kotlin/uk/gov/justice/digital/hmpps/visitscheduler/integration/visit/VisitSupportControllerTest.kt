package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_SUPPORT_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository

@DisplayName("Get $VISIT_SUPPORT_PATH")
class VisitSupportControllerTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @SpyBean
  private lateinit var supportTypeRepository: SupportTypeRepository

  @Autowired
  private lateinit var cacheManager: CacheManager

  @BeforeEach
  @AfterEach
  fun clearCache() {
    cacheManager.getCache("support-types")?.clear()
  }

  @Test
  fun `all available support is returned`() {
    // Give
    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.get().uri(VISIT_SUPPORT_PATH)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(5)

    verify(supportTypeRepository, times(1)).findAll()
  }

  @Test
  fun `when support is called twice cached values are returned the second time`() {
    // Give
    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    var responseSpec = webTestClient.get().uri(VISIT_SUPPORT_PATH)
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(5)

    // when a 2nd call is made results are still returned but from cache
    responseSpec = webTestClient.get().uri("/visit-support")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then results are the same as the first call
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(5)

    // verify that call to supportTypeRepository was only made once
    verify(supportTypeRepository, times(1)).findAll()
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val emptyRoles: List<String> = emptyList()

    // When

    val responseSpec = webTestClient.get().uri(VISIT_SUPPORT_PATH)
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
    val responseSpec = webTestClient.get().uri(VISIT_SUPPORT_PATH)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }
}
