package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerLevelMatcher

@Component
class SessionLocationValidator(
  private val prisonerLevelMatcher: PrisonerLevelMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isValid(
    sessionTemplate: SessionTemplate,
    prisonerHousingLevels: Map<PrisonerHousingLevels, String?>?,
  ): Boolean {
    return isSessionAvailableToPrisonerLocation(prisonerHousingLevels, sessionTemplate)
  }

  private fun isSessionAvailableToPrisonerLocation(
    prisonerLevels: Map<PrisonerHousingLevels, String?>?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionForAllPrisonerLocations = sessionTemplate.permittedSessionLocationGroups.isEmpty()
    if (isSessionForAllPrisonerLocations) {
      return true
    }

    // if prisoner levels not passed - location restricted sessions will not be returned
    return if (prisonerLevels == null) {
      false
    } else {
      if (sessionTemplate.includeLocationGroupType) {
        sessionTemplate.permittedSessionLocationGroups.any { prisonerLevelMatcher.test(it, prisonerLevels) }
      } else {
        sessionTemplate.permittedSessionLocationGroups.none { prisonerLevelMatcher.test(it, prisonerLevels) }
      }
    }
  }
}
