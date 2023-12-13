package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCheckingMatchingTemplatesOnUpdate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Post $FIND_MATCHING_SESSION_TEMPLATES_ON_CREATE")
class AdminCheckMatchingTemplatesOnUpdateTest : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private lateinit var sessionTemplateMondayToBeUpdated: SessionTemplate
  private lateinit var sessionTemplateMondayExisting: SessionTemplate

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create()

    sessionTemplateMondayToBeUpdated = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY, validToDate = LocalDate.now().plusYears(1))
    sessionTemplateMondayExisting = sessionTemplateEntityHelper.create(dayOfWeek = DayOfWeek.MONDAY)
  }

  @Test
  fun `when updated session template has same details as a different existing session template references are returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(SessionTemplateDto(sessionTemplateMondayToBeUpdated))
    val reference = sessionTemplateMondayToBeUpdated.reference

    // When
    val responseSpec = callCheckingMatchingTemplatesOnUpdate(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(1)
    Assertions.assertThat(matchingReferences[0]).isEqualTo(sessionTemplateMondayExisting.reference)
  }

  @Test
  fun `when updated session template does not have same details as an existing session template references are returned`() {
    // Given
    val dto = createUpdateSessionTemplateDto(
      SessionTemplateDto(sessionTemplateMondayToBeUpdated),
      SessionTimeSlotDto(LocalTime.of(13, 0), LocalTime.of(14, 0)),
    )
    val reference = sessionTemplateMondayToBeUpdated.reference

    // When
    val responseSpec = callCheckingMatchingTemplatesOnUpdate(webTestClient, reference, dto, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val matchingReferences = getCheckingMatchingTemplatesOnCreate(responseSpec)
    Assertions.assertThat(matchingReferences.size).isEqualTo(0)
  }
}
