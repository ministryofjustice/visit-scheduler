package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonerDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PrisonerIncentiveLevelMatcher

@Component
@SessionValidator(name = "Session incentive validator", description = "Validates if a session is available to a prisoner based on prisoner's incentive levels")
class SessionIncentiveValidator(
  private val incentiveLevelMatcher: PrisonerIncentiveLevelMatcher,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun isAvailableToPrisoner(
    sessionTemplate: SessionTemplate,
    prisoner: PrisonerDto?,
  ): Boolean {
    return isSessionAvailableToIncentiveLevel(prisoner?.incentiveLevel, sessionTemplate)
  }

  fun isSessionForAllIncentiveLevels(
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return sessionTemplate.permittedSessionIncentiveLevelGroups.isEmpty()
  }

  private fun isSessionAvailableToIncentiveLevel(
    prisonerIncentiveLevel: IncentiveLevel?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    if (!isSessionForAllIncentiveLevels(sessionTemplate)) {
      return incentiveLevelMatcher.test(prisonerIncentiveLevel, sessionTemplate)
    }
    return true
  }
}
