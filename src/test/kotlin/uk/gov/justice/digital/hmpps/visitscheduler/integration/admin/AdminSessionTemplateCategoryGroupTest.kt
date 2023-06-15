package uk.gov.justice.digital.hmpps.visitscheduler.integration.admin

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.CATEGORY_GROUP_ADMIN_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callDeleteCategoryGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetCategoryGroupByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetCategoryGroupsByPrisonId
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionCategoryRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository

@DisplayName("Get/Delete $CATEGORY_GROUP_ADMIN_PATH")
class AdminSessionTemplateCategoryGroupTest(
  @Autowired val testTemplateRepository: TestSessionTemplateRepository,
  @Autowired val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  @Autowired val testSessionCategoryGroupRepository: TestSessionCategoryGroupRepository,
  @Autowired val testSessionCategoryRepository: TestSessionCategoryRepository,
) : IntegrationTestBase() {

  private val adminRole = listOf("ROLE_VISIT_SCHEDULER_CONFIG")

  private var prison: Prison = Prison(code = "MDI", active = true)

  private lateinit var sessionTemplateWithGrps: SessionTemplate
  private lateinit var categoryGroup: SessionCategoryGroup
  private lateinit var categoryGroupWithNoSessionTemplate: SessionCategoryGroup

  @BeforeEach
  internal fun setUpTests() {
    sessionTemplateWithGrps = sessionTemplateEntityHelper.create(validFromDate = java.time.LocalDate.now())

    prison = prisonEntityHelper.create(prison.code, prison.active)

    categoryGroup = sessionCategoryGroupRepository.saveAndFlush(
      SessionCategoryGroup(
        prison = prison,
        prisonId = prison.id,
        name = "test 1",
      ),
    )

    categoryGroup.sessionCategories.add(
      testSessionCategoryRepository.saveAndFlush(
        SessionPrisonerCategory(
          sessionCategoryGroupId = categoryGroup.id,
          sessionCategoryGroup = categoryGroup,
          prisonerCategoryType = PrisonerCategoryType.A_PROVISIONAL,
        ),
      ),
    )

    categoryGroup.sessionTemplates.add(sessionTemplateWithGrps)
    sessionTemplateWithGrps.permittedSessionCategoryGroups.add(categoryGroup)
    sessionTemplateWithGrps = testTemplateRepository.saveAndFlush(sessionTemplateWithGrps)

    categoryGroupWithNoSessionTemplate = sessionCategoryGroupRepository.saveAndFlush(
      SessionCategoryGroup(
        prison = prison,
        prisonId = prison.id,
        name = "test 2",
      ),
    )

    categoryGroupWithNoSessionTemplate.sessionCategories.add(
      testSessionCategoryRepository.saveAndFlush(
        SessionPrisonerCategory(
          sessionCategoryGroupId = categoryGroupWithNoSessionTemplate.id,
          sessionCategoryGroup = categoryGroupWithNoSessionTemplate,
          prisonerCategoryType = PrisonerCategoryType.A_HIGH,
        ),
      ),
    )
  }

  @Test
  fun `get session category groups by prison id test`() {
    // Given
    val prisonCode = prison.code
    // When
    val responseSpec = callGetCategoryGroupsByPrisonId(webTestClient, prisonCode, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val groups = getSessionCategoryGroups(responseSpec)
    Assertions.assertThat(groups).hasSize(2)
    with(groups[0]) {
      Assertions.assertThat(name).isEqualTo(categoryGroup.name)
      Assertions.assertThat(reference).isEqualTo(categoryGroup.reference)
      Assertions.assertThat(categories).hasSize(1)
      Assertions.assertThat(categories[0]).isEqualTo(PrisonerCategoryType.A_PROVISIONAL)
    }
    with(groups[1]) {
      Assertions.assertThat(name).isEqualTo(categoryGroupWithNoSessionTemplate.name)
      Assertions.assertThat(reference).isEqualTo(categoryGroupWithNoSessionTemplate.reference)
      Assertions.assertThat(categories).hasSize(1)
      Assertions.assertThat(categories[0]).isEqualTo(PrisonerCategoryType.A_HIGH)
    }
  }

  @Test
  fun `get session group by reference test`() {
    // Given
    val reference = categoryGroup.reference
    // When
    val responseSpec = callGetCategoryGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk

    val group = getSessionCategoryGroup(responseSpec)
    with(group) {
      Assertions.assertThat(name).isEqualTo(categoryGroup.name)
      Assertions.assertThat(reference).isEqualTo(categoryGroup.reference)
      Assertions.assertThat(categories.size).isEqualTo(1)
      Assertions.assertThat(categories[0]).isEqualTo(PrisonerCategoryType.A_PROVISIONAL)
    }
  }

  @Test
  fun `delete session group by reference test`() {
    // Given
    val reference = categoryGroupWithNoSessionTemplate.reference

    // When
    val responseSpec = callDeleteCategoryGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isOk
    responseSpec.expectBody()
      .jsonPath("$").isEqualTo("Session category group Deleted $reference!")

    Assertions.assertThat(testSessionCategoryGroupRepository.hasById(sessionTemplateWithGrps.id)).isFalse
  }

  @Test
  fun `delete session group when session template uses the group exception is thrown`() {
    // Given
    val reference = categoryGroup.reference

    // When
    val responseSpec = callDeleteCategoryGroupByReference(webTestClient, reference, setAuthorisation(roles = adminRole))

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failed")
      .jsonPath("$.developerMessage").isEqualTo("Category group cannot be deleted $reference because session templates are using it!")
  }
}
