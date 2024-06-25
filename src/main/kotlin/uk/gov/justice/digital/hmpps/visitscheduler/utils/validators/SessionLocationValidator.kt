package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerLevelMatcher

@Component
@SessionValidator(name = "Session location validator", description = "Validates if a session is available to a prisoner based on their location")
class SessionLocationValidator(
  private val prisonerLevelMatcher: PrisonerLevelMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isAvailableToPrisoner(
    sessionTemplate: SessionTemplate,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>,
  ): Boolean {
    return isSessionAvailableToPrisonerLocation(prisonerHousingLevels, sessionTemplate)
  }

  private fun isSessionAvailableToPrisonerLocation(
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    if (isSessionForAllPrisonerLocations(sessionTemplate)) {
      return true
    }

    return if (sessionTemplate.includeLocationGroupType) {
      sessionTemplate.permittedSessionLocationGroups.any { prisonerLevelMatcher.test(it, prisonerLevels) }
    } else {
      sessionTemplate.permittedSessionLocationGroups.none { prisonerLevelMatcher.test(it, prisonerLevels) }
    }
  }

  fun isSessionForAllPrisonerLocations(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionTemplate.permittedSessionLocationGroups.isEmpty()
  }
}
