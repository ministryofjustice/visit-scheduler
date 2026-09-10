package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonVisitRequestRulesRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules.VisitRequestRuleFactory
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.SessionRequestInfo

@Service
class VisitRequestRuleCheckerService(
  private val prisonVisitRequestRulesRepository: PrisonVisitRequestRulesRepository,
  private val visitRequestRuleFactory: VisitRequestRuleFactory,
) {
  fun getRequestReviewReasons(sessionRequest: SessionRequestInfo): List<PrisonVisitRequestRuleType> {
    val failedRules = mutableListOf<PrisonVisitRequestRuleType>()
    val rules = prisonVisitRequestRulesRepository.findActiveVisitRequestRulesByPrison(sessionRequest.prisonCode)
    rules.forEach { rule ->
      checkVisitRequestRule(sessionRequest, rule)?.let {
        failedRules.add(it)
      }
    }

    return failedRules.toList()
  }

  private fun checkVisitRequestRule(sessionRequest: SessionRequestInfo, prisonVisitRequestRule: PrisonVisitRequestRules): PrisonVisitRequestRuleType? {
    val checkResult = visitRequestRuleFactory.getRuleChecker(prisonVisitRequestRule)?.ruleCheck(sessionRequest, prisonVisitRequestRule) ?: false
    if (checkResult) {
      return prisonVisitRequestRule.ruleName
    }

    return null
  }
}
