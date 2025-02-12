package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionCategoryValidator
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SessionCategoryValidatorTest {
  private val sessionCategoryValidator = SessionCategoryValidator(PrisonerCategoryMatcher())

  @Test
  fun `when prisoner's category is null sessions available to all prisoners will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", category = null)

    // When
    val result = sessionCategoryValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's category is available sessions available to all prisoners will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", category = "C")

    // When
    val result = sessionCategoryValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's category is same as session category allowed for a session that session will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionCategoryGroup = createSessionCategoryGroup(
      categories = listOf(PrisonerCategoryType.C),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionCategoryGroups.add(sessionCategoryGroup)

    val prisonerDto = PrisonerDto(prisonerId = "AA1122", category = "C")

    // When
    val result = sessionCategoryValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's category is in the list of session categories allowed for a session that session will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionCategoryGroup = createSessionCategoryGroup(
      categories = listOf(PrisonerCategoryType.C, PrisonerCategoryType.D, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_STANDARD),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionCategoryGroups.add(sessionCategoryGroup)

    val prisonerDto = PrisonerDto(prisonerId = "AA1122", category = PrisonerCategoryType.A_HIGH.code)

    // When
    val result = sessionCategoryValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's category is not in the list of session categories allowed for a session that session will be unavailable to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionCategoryGroup = createSessionCategoryGroup(
      categories = listOf(PrisonerCategoryType.C, PrisonerCategoryType.D, PrisonerCategoryType.A_HIGH, PrisonerCategoryType.A_STANDARD),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionCategoryGroups.add(sessionCategoryGroup)

    // prisoner category is A_PROVISIONAL - so not allowed on the session
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", category = PrisonerCategoryType.A_PROVISIONAL.code)

    // When
    val result = sessionCategoryValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isFalse()
  }

  private fun createSessionTemplate(): SessionTemplate = sessionTemplate(
    validFromDate = LocalDate.now(),
  )

  private fun createSessionCategoryGroup(groupName: String = "group 1", categories: List<PrisonerCategoryType>, prison: Prison): SessionCategoryGroup {
    val group = SessionCategoryGroup(
      name = groupName,
      prisonId = prison.id,
      prison = prison,
    )

    val permittedSessionCategories = mutableListOf<SessionPrisonerCategory>()
    for (category in categories) {
      permittedSessionCategories.add(createPermittedSessionCategory(category, group))
    }

    group.sessionCategories.addAll(permittedSessionCategories)
    return group
  }

  private fun createPermittedSessionCategory(
    prisonerCategoryType: PrisonerCategoryType,
    group: SessionCategoryGroup,
  ): SessionPrisonerCategory = SessionPrisonerCategory(
    sessionCategoryGroupId = group.id,
    sessionCategoryGroup = group,
    prisonerCategoryType = prisonerCategoryType,
  )
}
