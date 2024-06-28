package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionIncentiveValidator
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class SessionIncentiveLevelValidatorTest {
  private val sessionIncentiveValidator = SessionIncentiveValidator(PrisonerIncentiveLevelMatcher())

  @Test
  fun `when prisoner's incentive level is null sessions available to all prisoners will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", incentiveLevel = null)

    // When
    val result = sessionIncentiveValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's incentive level is available sessions available to all prisoners will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", incentiveLevel = IncentiveLevel.BASIC)

    // When
    val result = sessionIncentiveValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's incentive level is same as session incentive allowed for a session that session will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionIncentiveLevelGroup = createSessionIncentiveLevelGroup(
      incentiveLevels = listOf(IncentiveLevel.ENHANCED),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionIncentiveLevelGroups.add(sessionIncentiveLevelGroup)

    val prisonerDto = PrisonerDto(prisonerId = "AA1122", incentiveLevel = IncentiveLevel.ENHANCED)

    // When
    val result = sessionIncentiveValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's incentive level is in the list of session iIncentive levels allowed for a session that session will be available to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionIncentiveLevelGroup = createSessionIncentiveLevelGroup(
      incentiveLevels = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionIncentiveLevelGroups.add(sessionIncentiveLevelGroup)

    val prisonerDto = PrisonerDto(prisonerId = "AA1122", incentiveLevel = IncentiveLevel.ENHANCED)

    // When
    val result = sessionIncentiveValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isTrue()
  }

  @Test
  fun `when prisoner's incentive level is not in the list of session iIncentive levels allowed for a session that session will be unavailable to this prisoner`() {
    // Given
    val sessionTemplate = createSessionTemplate()

    val sessionIncentiveLevelGroup = createSessionIncentiveLevelGroup(
      incentiveLevels = listOf(IncentiveLevel.ENHANCED, IncentiveLevel.ENHANCED_2, IncentiveLevel.ENHANCED_3),
      prison = sessionTemplate.prison,
    )

    sessionTemplate.permittedSessionIncentiveLevelGroups.add(sessionIncentiveLevelGroup)

    // prisoner incentive level is STANDARD - so not allowed on the session
    val prisonerDto = PrisonerDto(prisonerId = "AA1122", incentiveLevel = IncentiveLevel.STANDARD)

    // When
    val result = sessionIncentiveValidator.isValid(prisoner = prisonerDto, sessionTemplate = sessionTemplate)
    // Then
    assertThat(result).isFalse()
  }

  private fun createSessionTemplate(): SessionTemplate {
    return sessionTemplate(
      validFromDate = LocalDate.now(),
    )
  }

  private fun createSessionIncentiveLevelGroup(groupName: String = "group 1", incentiveLevels: List<IncentiveLevel>, prison: Prison): SessionIncentiveLevelGroup {
    val group = SessionIncentiveLevelGroup(
      name = groupName,
      prisonId = prison.id,
      prison = prison,
    )

    val permittedSessionIncentiveLevels = mutableListOf<SessionPrisonerIncentiveLevel>()
    for (incentive in incentiveLevels) {
      permittedSessionIncentiveLevels.add(createPermittedSessionIncentiveLevel(incentive, group))
    }

    group.sessionIncentiveLevels.addAll(permittedSessionIncentiveLevels)
    return group
  }

  private fun createPermittedSessionIncentiveLevel(
    prisonerIncentiveLevel: IncentiveLevel,
    group: SessionIncentiveLevelGroup,
  ): SessionPrisonerIncentiveLevel {
    return SessionPrisonerIncentiveLevel(
      sessionIncentiveGroupId = group.id,
      sessionIncentiveLevelGroup = group,
      prisonerIncentiveLevel = prisonerIncentiveLevel,
    )
  }
}
