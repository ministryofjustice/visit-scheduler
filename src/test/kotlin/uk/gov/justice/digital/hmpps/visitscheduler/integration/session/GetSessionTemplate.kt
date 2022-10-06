package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDate

@DisplayName("Get /visit-session-templates")
class GetSessionTemplate() : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateEntityHelper.deleteAll()

  @Test
  fun `all session templates are returned empty list`() {

    // Given

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `all session templates are returned`() {
    // Given
    sessionTemplateEntityHelper.create(validFromDate = LocalDate.parse("2021-01-01"))

    sessionTemplateEntityHelper.create(validFromDate = LocalDate.parse("2021-02-01"))

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
  }

  @Test
  fun `session templates are returned by id`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.parse("2021-01-01"))

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates/${sessionTemplate.id}")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(10)
      .jsonPath("$.sessionTemplateId").isEqualTo(sessionTemplate.id)
      .jsonPath("$.prisonId").isEqualTo("MDI")
      .jsonPath("$.startTime").isEqualTo("09:00:00")
      .jsonPath("$.endTime").isEqualTo("10:00:00")
      .jsonPath("$.validFromDate").isEqualTo("2021-01-01")
      .jsonPath("$.visitType").isEqualTo(VisitType.SOCIAL.name)
      .jsonPath("$.visitRoom").isEqualTo("3B")
      .jsonPath("$.closedCapacity").isEqualTo(5)
      .jsonPath("$.openCapacity").isEqualTo(10)
  }
}
