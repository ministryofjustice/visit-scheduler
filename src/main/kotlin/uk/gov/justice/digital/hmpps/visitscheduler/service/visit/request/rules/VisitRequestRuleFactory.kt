package uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.MaxVisitsPerMonthVisitRequestRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.VisitIntervalVisitRequestRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.VisitRequestRule

@Service
class VisitRequestRuleFactory(
  private val visitIntervalRule: VisitIntervalVisitRequestRule,
  private val maxVisitsPerMonthRule: MaxVisitsPerMonthVisitRequestRule,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getRuleChecker(prisonVisitRequestRules: PrisonVisitRequestRules): VisitRequestRule<Application>? = when (prisonVisitRequestRules.ruleName) {
    PrisonVisitRequestRuleType.VISIT_INTERVAL -> visitIntervalRule
    PrisonVisitRequestRuleType.VISITS_PER_MONTH -> maxVisitsPerMonthRule
  }
}
