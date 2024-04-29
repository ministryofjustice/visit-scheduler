package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.util.function.BiPredicate
import java.util.stream.Collectors

@Component
class PrisonerIncentiveLevelMatcher : BiPredicate<IncentiveLevel?, SessionTemplate> {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override fun test(
    prisonerIncentiveLevel: IncentiveLevel?,
    sessionTemplate: SessionTemplate,
  ): Boolean {
    return isPrisonerIncentiveLevelAllowedOnSession(sessionTemplate, prisonerIncentiveLevel)
  }

  fun isPrisonerIncentiveLevelAllowedOnSession(sessionTemplate: SessionTemplate, prisonerIncentiveLevel: IncentiveLevel?): Boolean {
    prisonerIncentiveLevel?.let {
      return getAllowedIncentiveLevelsForSessionTemplate(sessionTemplate).any { incentiveLevel ->
        val match = incentiveLevel.equals(it.code, false)
        LOG.debug("isPrisonerIncentiveLevelAllowedOnSession prisonerIncentiveLevel: ${it.code}, matched $match, sessionTemplate:${sessionTemplate.reference}")
        match
      }
    }

    // if prisoner incentive level is null - return false as prisoner should not be allowed on restricted incentive level sessions
    return false
  }

  private fun getAllowedIncentiveLevelsForSessionTemplate(sessionTemplate: SessionTemplate): Set<String> {
    return sessionTemplate.permittedSessionIncentiveLevelGroups.stream()
      .flatMap { it.sessionIncentiveLevels.stream() }
      .map { it.prisonerIncentiveLevel.code }
      .collect(Collectors.toSet())
  }
}
