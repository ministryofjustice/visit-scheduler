package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionCategoryValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionIncentiveValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionLocationValidator

const val LOCATION_NOT_PERMITTED = -10

@Component
class PrisonerSessionValidator(
  private val levelMatcher: PrisonerLevelMatcher,
  private val sessionLocationValidator: SessionLocationValidator,
  private val sessionCategoryValidator: SessionCategoryValidator,
  private val sessionIncentiveValidator: SessionIncentiveValidator,
) {
  fun getLocationScore(
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
    sessionTemplate: SessionTemplate,
  ): Int {
    if (sessionLocationValidator.isAvailableToPrisoner(sessionTemplate, prisonerLevels)) {
      return if (sessionLocationValidator.isSessionForAllPrisonerLocations(sessionTemplate) || !sessionTemplate.includeLocationGroupType) {
        0
      } else {
        getHighestLevelScore(sessionTemplate, prisonerLevels)
      }
    }

    return LOCATION_NOT_PERMITTED
  }

  private fun getHighestLevelScore(
    sessionTemplate: SessionTemplate,
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
  ): Int {
    var highestScore = LOCATION_NOT_PERMITTED // If minus 10 then it does not match at all should be rejected
    sessionTemplate.permittedSessionLocationGroups.forEach { sessionGroup ->
      sessionGroup.sessionLocations.forEach { permittedSessionLocation ->
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
    return highestScore
  }

  fun isSessionForAllCategories(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionCategoryValidator.isSessionForAllCategoryLevels(sessionTemplate)
  }

  fun isSessionForAllIncentiveLevels(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionIncentiveValidator.isSessionForAllIncentiveLevels(sessionTemplate)
  }
}
