package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerCategoryMatcher

@Component
class SessionCategoryValidator(
  private val prisonerCategoryMatcher: PrisonerCategoryMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isValid(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto?,
  ): Boolean = isSessionAvailableToCategory(prisoner?.category, sessionTemplate)

  private fun isSessionAvailableToCategory(
    prisonerCategory: String?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionForAllCategoryLevels = sessionTemplate.permittedSessionCategoryGroups.isEmpty()

    return if (!isSessionForAllCategoryLevels) {
      prisonerCategoryMatcher.test(prisonerCategory, sessionTemplate)
    } else {
      true
    }
  }
}
