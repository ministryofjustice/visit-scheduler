package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerIncentiveLevelMatcher

@Component
class SessionIncentiveValidator(
  private val incentiveLevelMatcher: PrisonerIncentiveLevelMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isValid(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto?,
  ): Boolean = isSessionAvailableToIncentiveLevel(prisoner?.incentiveLevel, sessionTemplate)

  private fun isSessionAvailableToIncentiveLevel(
    prisonerIncentiveLevel: IncentiveLevel?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    val isSessionForAllIncentiveLevels = sessionTemplate.permittedSessionIncentiveLevelGroups.isEmpty()
    if (!isSessionForAllIncentiveLevels) {
      return incentiveLevelMatcher.test(prisonerIncentiveLevel, sessionTemplate)
    }
    return true
  }
}
