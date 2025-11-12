package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.DEACTIVATE_SESSION_TEMPLATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callActivateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeActivateSessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Session template tests for activate - $ACTIVATE_SESSION_TEMPLATE and deactivate  $DEACTIVATE_SESSION_TEMPLATE")
class AdminSessionTemplateTest(
  @param:Autowired private val testTemplateRepository: TestSessionTemplateRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplateActive: SessionTemplate
  private lateinit var sessionTemplateInactive: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    sessionTemplateActive = sessionTemplateEntityHelper.create(
      name = "active session template",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("09:00"),
      endTime = LocalTime.parse("10:00"),
      isActive = true,
    )
    sessionTemplateInactive = sessionTemplateEntityHelper.create(
      name = "inactive session template",
      validFromDate = LocalDate.now(),
      startTime = LocalTime.parse("11:00"),
      endTime = LocalTime.parse("12:00"),
      isActive = false,
    )

    testTemplateRepository.saveAndFlush(sessionTemplateActive)
    testTemplateRepository.saveAndFlush(sessionTemplateInactive)
  }

  @Test
  fun `when inactive session activated then session template active flag is true`() {
    // Given
    val reference = sessionTemplateInactive.reference

    // When
    val responseSpec = callActivateSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    Assertions.assertThat(sessionTemplateDto.active).isTrue
  }

  @Test
  fun `when active session deactivated then session template active flag is false`() {
    // Given
    val reference = sessionTemplateActive.reference

    // When
    val responseSpec = callDeActivateSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    val sessionTemplateDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, SessionTemplateDto::class.java)

    Assertions.assertThat(sessionTemplateDto.active).isFalse
  }

  @Test
  fun `when non existing session activated then BAD_REQUEST error is thrown`() {
    // Given
    val reference = "i-do-not-exist"

    // When
    val responseSpec = callActivateSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:i-do-not-exist not found")
  }

  @Test
  fun `when non existing session deactivated then BAD_REQUEST error is thrown`() {
    // Given
    val reference = "i-do-not-exist"

    // When
    val responseSpec = callDeActivateSessionTemplate(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:i-do-not-exist not found")
  }
}
