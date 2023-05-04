package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateSessionTemplateByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createUpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType

@DisplayName("Get /visit-sessions")
class UpdateSessionsTemplateTest : IntegrationTestBase() {

  private val requiredRole = listOf("ROLE_VISIT_SCHEDULER")

  private var prison: Prison = Prison(code = "MDI", active = true)
  private lateinit var sessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUpTests() {
    prison = prisonEntityHelper.create(prison.code, prison.active)
    sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = prison.code)
  }

  @Test
  fun `update session template`() {
    // Given
    val allowedPermittedLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))
    val sessionGroup = sessionLocationGroupHelper.create(prisonCode = prison.code, prisonHierarchies = allowedPermittedLocations)

    val categoryAs = listOf(PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_STANDARD, PrisonerCategoryType.A_EXCEPTIONAL, PrisonerCategoryType.A_PROVISIONAL)
    val sessionCategoryGroup = sessionPrisonerCategoryHelper.create(prisonCode = prison.code, prisonerCategories = categoryAs)

    val dto = createUpdateSessionTemplateDto(
      name = sessionTemplate.name + " Updated",
      validFromDate = sessionTemplate.validFromDate.plusDays(1),
      validToDate = sessionTemplate.validToDate?.plusDays(10),
      closedCapacity = sessionTemplate.closedCapacity + 1,
      openCapacity = sessionTemplate.openCapacity + 1,
      startTime = sessionTemplate.startTime.plusHours(1),
      endTime = sessionTemplate.endTime.plusHours(2),
      dayOfWeek = sessionTemplate.dayOfWeek.minus(1),
      locationGroupReferences = mutableListOf(sessionGroup.reference),
      biWeekly = !sessionTemplate.biWeekly,
      categoryGroupReferences = mutableListOf(sessionCategoryGroup.reference),
    )

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, sessionTemplate.reference, dto, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isOk

    val sessionTemplateDto = getSessionTemplate(responseSpec)
    Assertions.assertThat(sessionTemplateDto.name).isEqualTo(dto.name)
    Assertions.assertThat(sessionTemplateDto.validFromDate).isEqualTo(dto.validFromDate)
    Assertions.assertThat(sessionTemplateDto.validToDate).isEqualTo(dto.validToDate)
    Assertions.assertThat(sessionTemplateDto.closedCapacity).isEqualTo(dto.closedCapacity)
    Assertions.assertThat(sessionTemplateDto.openCapacity).isEqualTo(dto.openCapacity)
    Assertions.assertThat(sessionTemplateDto.startTime).isEqualTo(dto.startTime)
    Assertions.assertThat(sessionTemplateDto.endTime).isEqualTo(dto.endTime)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.permittedLocationGroups[0].reference).isEqualTo(dto.locationGroupReferences!![0])
    Assertions.assertThat(sessionTemplateDto.biWeekly).isEqualTo(dto.biWeekly)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.size).isEqualTo(1)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups.stream().map { it.categories }).containsExactlyInAnyOrder(categoryAs)
    Assertions.assertThat(sessionTemplateDto.prisonerCategoryGroups[0].reference).isEqualTo(dto.categoryGroupReferences!![0])
  }

  @Test
  fun `exception thrown when reference not found during update session template`() {
    // Given
    val dto = createUpdateSessionTemplateDto()
    val reference = "Ref1234"

    // When
    val responseSpec = callUpdateSessionTemplateByReference(webTestClient, reference, dto, setAuthorisation(roles = requiredRole))

    // Then
    responseSpec.expectStatus().isNotFound
    val errorResponse = getErrorResponse(responseSpec)
    Assertions.assertThat(errorResponse.userMessage).isEqualTo("Template not found: null")
    Assertions.assertThat(errorResponse.developerMessage).isEqualTo("Template reference:$reference not found")
  }
}
