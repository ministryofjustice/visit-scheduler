package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.util.function.Predicate

const val LOCATION_NOT_PERMITTED = -10

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
    if (!isSessionForAllPrisonerLocations(sessionTemplate)) {
      return if (sessionTemplate.includeLocationGroupType) {
        sessionTemplate.permittedSessionLocationGroups.any { levelMatcher.test(it, prisonerLevels) }
      } else {
        sessionTemplate.permittedSessionLocationGroups.none { levelMatcher.test(it, prisonerLevels) }
      }
    }

    return true
  }

  fun isSessionAvailableToPrisonerCategory(
    prisonerCategory: String?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    if (!isSessionForAllCategories(sessionTemplate)) {
      return categoryMatcher.test(prisonerCategory, sessionTemplate)
    }
    return true
  }

  fun isSessionAvailableToIncentiveLevel(
    prisonerIncentiveLevel: IncentiveLevel?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    if (!isSessionForAllIncentiveLevels(sessionTemplate)) {
      return incentiveLevelMatcher.test(prisonerIncentiveLevel, sessionTemplate)
    }
    return true
  }

  fun isSessionForAllPrisonerLocations(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionAllPrisonersMatcher.test(sessionTemplate)
  }

  fun isSessionForAllCategories(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionAllPrisonersCategoryMatcher.test(sessionTemplate)
  }

  fun isSessionForAllIncentiveLevels(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionAllPrisonersIncentiveLevelMatcher.test(sessionTemplate)
  }

  fun getLocationScore(
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
    sessionTemplate: SessionTemplate,
  ): Int {
    return if (!isSessionAvailableToPrisonerLocation(prisonerLevels, sessionTemplate)) {
      LOCATION_NOT_PERMITTED
    } else {
      if (isSessionForAllPrisonerLocations(sessionTemplate) || !sessionTemplate.includeLocationGroupType) {
        0
      } else {
        var highestScore = LOCATION_NOT_PERMITTED // If minus 10 then it does not match at all should be rejected
        sessionTemplate.permittedSessionLocationGroups.forEach { sessionGroup ->
          for (permittedSessionLocation in sessionGroup.sessionLocations) {
            with(permittedSessionLocation) {
              if (levelMatcher.hasLevelMatch(permittedSessionLocation, prisonerLevels)) {
                val score = levelFourCode?.let { 4 } ?: levelThreeCode?.let { 3 } ?: levelTwoCode?.let { 2 } ?: levelOneCode.let { 1 }
                if (score > highestScore) {
                  highestScore = score
                }
              }
            }
          }
        }
        highestScore
      }
    }
  }
}
