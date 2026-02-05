package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonVisitRequestRulesRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules.VisitRequestRuleFactory

@Service
class VisitRequestRuleCheckerService(
  private val prisonVisitRequestRulesRepository: PrisonVisitRequestRulesRepository,
  private val visitRequestRuleFactory: VisitRequestRuleFactory,
) {
  fun getRequestReviewReasons(application: Application): List<PrisonVisitRequestRuleType> {
    val failedRules = mutableListOf<PrisonVisitRequestRuleType>()
    prisonVisitRequestRulesRepository.findActiveVisitRequestRulesByPrison(application.prison.code).forEach { rule ->
      checkVisitRequestRule(application, rule)?.let {
        failedRules.add(it)
      }
    }

    return failedRules.toList()
  }

  private fun checkVisitRequestRule(application: Application, prisonVisitRequestRule: PrisonVisitRequestRules): PrisonVisitRequestRuleType? {
    val checkResult = visitRequestRuleFactory.getRuleChecker(prisonVisitRequestRule)?.ruleCheck(application, prisonVisitRequestRule) ?: false
    if (checkResult) {
      return prisonVisitRequestRule.ruleName
    }

    return null
  }
}
