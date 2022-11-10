package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@DisplayName("Get /visit-session-templates")
class GetSessionTemplate(
  @Autowired private val objectMapper: ObjectMapper,
  @Autowired private val repository: SessionTemplateRepository
) : IntegrationTestBase() {

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
    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())
    sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

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
    val sessionTemplate = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now())

    repository.save(sessionTemplate)

    // When
    val responseSpec = webTestClient.get().uri("/visit-session-templates/${sessionTemplate.id}")
      .headers(setAuthorisation(roles = requiredRole))
      .exchange()

    // Then
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    Assertions.assertThat(sessionTemplateDto.sessionTemplateId).isEqualTo(sessionTemplate.id)
    Assertions.assertThat(sessionTemplateDto.prisonId).isEqualTo(sessionTemplate.prisonId)
    Assertions.assertThat(sessionTemplateDto.startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionTemplateDto.endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionTemplateDto.validFromDate).isEqualTo(LocalDate.now().format(DateTimeFormatter.ISO_DATE))
    Assertions.assertThat(sessionTemplateDto.visitType).isEqualTo(VisitType.SOCIAL)
    Assertions.assertThat(sessionTemplateDto.visitRoom).isEqualTo(sessionTemplate.visitRoom)
    Assertions.assertThat(sessionTemplateDto.closedCapacity).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.openCapacity).isEqualTo(sessionTemplate.openCapacity)
  }
}
