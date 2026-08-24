package uk.gov.justice.digital.hmpps.visitscheduler.helper

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleConfigType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRulesConfig
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonVisitRequestRulesConfigRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonVisitRequestRulesRepository

@Component
@Transactional
class VisitRequestRuleHelper(
  private val prisonVisitRequestRulesRepository: PrisonVisitRequestRulesRepository,
  private val prisonVisitRequestRulesConfigRepository: PrisonVisitRequestRulesConfigRepository,
  private val prisonEntityHelper: PrisonEntityHelper,
) {
  fun createMaxVisitsPerMonthRule(
    prisonCode: String = "MDI",
    visitsPerMonth: Int,
  ) {
    create(prisonCode, PrisonVisitRequestRuleType.VISITS_PER_MONTH, mapOf(PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH to visitsPerMonth.toString()))
  }

  fun createVisitIntervalRule(
    prisonCode: String = "MDI",
    allowedVisits: Int,
    intervalDays: Int,
  ) {
    val attributeNameAndValueMap = mapOf(
      PrisonVisitRequestRuleConfigType.VISITS_ALLOWED to allowedVisits.toString(),
      PrisonVisitRequestRuleConfigType.INTERVAL_DAYS_BEFORE_AND_AFTER to intervalDays.toString(),
    )
    create(prisonCode, PrisonVisitRequestRuleType.VISIT_INTERVAL, attributeNameAndValueMap)
  }

  fun createAlreadyRejectedRequestRule(
    prisonCode: String = "MDI",
    rejectionIntervalInHours: Int,
    totalRejectedVisits: Int,
  ) {
    val attributeNameAndValueMap = mapOf(
      PrisonVisitRequestRuleConfigType.REJECTION_INTERVAL_IN_HOURS to rejectionIntervalInHours.toString(),
      PrisonVisitRequestRuleConfigType.TOTAL_REJECTIONS to totalRejectedVisits.toString(),
    )
    create(prisonCode, PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT, attributeNameAndValueMap)
  }

  private fun create(
    prisonCode: String = "MDI",
    ruleName: PrisonVisitRequestRuleType,
    attributeNameAndValueMap: Map<PrisonVisitRequestRuleConfigType, String>,
  ): PrisonVisitRequestRules {
    val prison = prisonEntityHelper.create(prisonCode)

    var prisonVisitRequestRules = PrisonVisitRequestRules(
      prisonId = prison.id,
      prison = prison,
      ruleName = ruleName,
      active = true,
    )
    prisonVisitRequestRules = prisonVisitRequestRulesRepository.saveAndFlush(prisonVisitRequestRules)

    attributeNameAndValueMap.forEach { (attributeName, attributeValue) ->
      var prisonVisitRequestRulesConfig = PrisonVisitRequestRulesConfig(
        prisonVisitRequestRuleId = prisonVisitRequestRules.id,
        prisonVisitRequestRule = prisonVisitRequestRules,
        attributeName = attributeName,
        attributeValue = attributeValue,
      )

      prisonVisitRequestRulesConfig = prisonVisitRequestRulesConfigRepository.saveAndFlush(prisonVisitRequestRulesConfig)
      prisonVisitRequestRules.prisonVisitRequestRulesConfig.add(prisonVisitRequestRulesConfig)
    }
    return prisonVisitRequestRules
  }
}
