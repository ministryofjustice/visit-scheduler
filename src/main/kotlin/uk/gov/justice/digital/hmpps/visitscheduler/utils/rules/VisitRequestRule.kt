package uk.gov.justice.digital.hmpps.visitscheduler.utils.rules

import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules

interface VisitRequestRule<Application> {
  fun ruleCheck(application: Application, prisonVisitRequestRules: PrisonVisitRequestRules): Boolean

  fun getConfigValues(prisonVisitRequestRules: PrisonVisitRequestRules): Map<PrisonVisitRequestRuleConfigType, String?> {
    val attributeValues = mutableMapOf<PrisonVisitRequestRuleConfigType, String?>()
    prisonVisitRequestRules.ruleName.config.forEach { configType ->
      val attributeValue = prisonVisitRequestRules.prisonVisitRequestRulesConfig.firstOrNull { it.attributeName == configType }?.attributeValue
      attributeValues[configType] = attributeValue
    }

    return attributeValues
  }
}
