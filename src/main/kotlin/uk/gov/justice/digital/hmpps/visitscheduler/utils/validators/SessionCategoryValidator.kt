package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerCategoryMatcher

@Component
@SessionValidator(
  name = "Session category validator",
  description = "Validates if a session is available to a prisoner based on prisoner's category",
)
class SessionCategoryValidator(
  private val prisonerCategoryMatcher: PrisonerCategoryMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isAvailableToPrisoner(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto?,
  ): Boolean {
    return isSessionAvailableToCategory(prisoner?.category, sessionTemplate)
  }

  fun isSessionForAllCategoryLevels(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionTemplate.permittedSessionCategoryGroups.isEmpty()
  }

  private fun isSessionAvailableToCategory(
    prisonerCategory: String?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    if (!isSessionForAllCategoryLevels(sessionTemplate)) {
      return prisonerCategoryMatcher.test(prisonerCategory, sessionTemplate)
    }
    return true
  }
}
