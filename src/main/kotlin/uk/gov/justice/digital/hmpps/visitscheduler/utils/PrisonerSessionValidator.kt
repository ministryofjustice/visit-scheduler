package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevels
import java.util.function.Predicate

@Component
class PrisonerSessionValidator(
  private val levelMatcher: PrisonerLevelMatcher,
  private val categoryMatcher: PrisonerCategoryMatcher,
  private val incentiveLevelMatcher: PrisonerIncentiveLevelMatcher,
) {
  private val sessionAllPrisonersMatcher =
    Predicate<SessionTemplate> { sessionTemplate -> sessionTemplate.permittedSessionLocationGroups.isEmpty() }

  private val sessionAllPrisonersCategoryMatcher =
    Predicate<SessionTemplate> { sessionTemplate -> sessionTemplate.permittedSessionCategoryGroups.isEmpty() }

  private val sessionAllPrisonersIncentiveLevelMatcher =
    Predicate<SessionTemplate> { sessionTemplate -> sessionTemplate.permittedSessionIncentiveLevelGroups.isEmpty() }

  fun isSessionAvailableToPrisonerLocation(
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionAvailableToAllPrisoners = sessionAllPrisonersMatcher.test(sessionTemplate)
    if (!isSessionAvailableToAllPrisoners) {
      return sessionTemplate.permittedSessionLocationGroups.any { levelMatcher.test(it, prisonerLevels) }
    }

    return true
  }

  fun isSessionAvailableToPrisonerCategory(
    prisonerCategory: String?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionAvailableToAllPrisoners = sessionAllPrisonersCategoryMatcher.test(sessionTemplate)
    if (!isSessionAvailableToAllPrisoners) {
      return categoryMatcher.test(prisonerCategory, sessionTemplate)
    }

    return true
  }

  fun isSessionAvailableToIncentiveLevel(
    prisonerIncentiveLevel: IncentiveLevels?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionAvailableToAllPrisoners = sessionAllPrisonersIncentiveLevelMatcher.test(sessionTemplate)
    if (!isSessionAvailableToAllPrisoners) {
      return incentiveLevelMatcher.test(prisonerIncentiveLevel, sessionTemplate)
    }

    return true
  }
}
