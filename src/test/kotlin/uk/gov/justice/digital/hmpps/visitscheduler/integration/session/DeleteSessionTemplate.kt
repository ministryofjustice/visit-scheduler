package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.LocalTime

@DisplayName("DELETE /visit-session-templates/{sessionTemplateId}")
class DeleteSessionTemplate() : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  @Test
  fun `delete session template by id`() {
    // Given

    val sessionTemplate = sessionTemplateEntityHelper.create(
      startTime = LocalTime.of(10, 0),
      endTime = LocalTime.of(12, 0)
    )

    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When
    val responseSpec = webTestClient.delete().uri("/visit-session-templates/${sessionTemplate.id}")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk

    webTestClient.get().uri("/visit-session-templates/${sessionTemplate.id}")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isNotFound

    verify(telemetryClient).trackEvent(
      eq("session-template-deleted"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["id"]).isEqualTo(sessionTemplate.id.toString())
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("session-template-deleted"), any(), isNull())
  }

  @Test
  fun `delete session template by id NOT FOUND`() {

    // Given
    val fakeID = 1234569999999999
    val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

    // When

    val responseSpec = webTestClient.delete().uri("/visit-session-templates/$fakeID")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk

    verify(telemetryClient, times(0)).trackEvent(eq("session-template-deleted"), any(), isNull())
  }
}
