package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.PrisonerHousingLevels
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.util.function.Predicate

@Component
class PrisonerSessionValidator(
  private val levelMatcher: PrisonerLevelMatcher
) {
  private val sessionAllPrisonersMatcher =
    Predicate<SessionTemplate> { sessionTemplate -> sessionTemplate.permittedSessionLocations.isNullOrEmpty() }

  fun isSessionAvailableToPrisoner(
    prisonerLevels: Map<PrisonerHousingLevels, String?>,
    sessionTemplate: SessionTemplate
  ): Boolean {
    val isSessionAvailableToAllPrisoners = sessionAllPrisonersMatcher.test(sessionTemplate)
    if (!isSessionAvailableToAllPrisoners) {
      return levelMatcher.test(sessionTemplate.permittedSessionLocations, prisonerLevels)
    }

    return true
  }
}
