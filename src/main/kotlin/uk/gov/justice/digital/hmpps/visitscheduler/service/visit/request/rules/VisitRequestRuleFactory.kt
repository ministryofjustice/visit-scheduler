package uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.MaxVisitsPerMonthRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.Rule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.VisitIntervalRule

@Service
class VisitRequestRuleFactory(
  private val visitRepository: VisitRepository,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getRuleChecker(prisonVisitRequestRules: PrisonVisitRequestRules): Rule<Application>? = when (prisonVisitRequestRules.ruleName) {
    PrisonVisitRequestRuleType.VISIT_INTERVAL -> getVisitIntervalRuleInstance(prisonVisitRequestRules)
    PrisonVisitRequestRuleType.VISITS_PER_MONTH -> getMaxVisitsPerMonthRuleInstance(prisonVisitRequestRules)
  }

  private fun getVisitIntervalRuleInstance(prisonVisitRequestRules: PrisonVisitRequestRules): VisitIntervalRule? {
    val interval = try {
      prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == PrisonVisitRequestRuleConfigType.NUMBER_OF_DAYS }?.attributeValue?.toIntOrNull()
    } catch (e: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting number of days for visit interval rule : ${prisonVisitRequestRules.ruleName} for prison ${prisonVisitRequestRules.prison.code}", e)
      null
    }

    if (interval != null) {
      return VisitIntervalRule(interval, visitRepository)
    } else {
      logger.error("Invalid number of days for visit interval rule : ${prisonVisitRequestRules.ruleName.name} for prison ${prisonVisitRequestRules.prison.code}")
    }

    return null
  }

  private fun getMaxVisitsPerMonthRuleInstance(prisonVisitRequestRules: PrisonVisitRequestRules): MaxVisitsPerMonthRule? {
    val maxVisitsPerMonth = try {
      prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH }?.attributeValue?.toIntOrNull()
    } catch (e: NumberFormatException) {
      logger.error("NumberFormatException thrown while getting max visits per month for visits per month rule : ${prisonVisitRequestRules.ruleName.name} for prison ${prisonVisitRequestRules.prison.code}", e)
      null
    }

    if (maxVisitsPerMonth != null) {
      return MaxVisitsPerMonthRule(maxVisitsPerMonth, visitRepository)
    } else {
      logger.error("Invalid number of days for visit interval rule : ${prisonVisitRequestRules.ruleName.name} for prison ${prisonVisitRequestRules.prison.code}")
    }

    return null
  }
}
