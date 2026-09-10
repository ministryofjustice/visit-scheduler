package uk.gov.justice.digital.hmpps.visitscheduler.service.visit.request.rules

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonVisitRequestRuleType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonVisitRequestRules
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.AlreadyRejectedVisitRequestRejectionRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.MaxVisitsPerMonthVisitRequestRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.SessionRequestInfo
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.VisitIntervalVisitRequestRule
import uk.gov.justice.digital.hmpps.visitscheduler.utils.rules.session.VisitRequestRule

@Service
class VisitRequestRuleFactory(
  private val visitIntervalRule: VisitIntervalVisitRequestRule,
  private val maxVisitsPerMonthRule: MaxVisitsPerMonthVisitRequestRule,
  private val alreadyRejectedVisitRequestRejectionRule: AlreadyRejectedVisitRequestRejectionRule,
) {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getRuleChecker(prisonVisitRequestRules: PrisonVisitRequestRules): VisitRequestRule<SessionRequestInfo>? = when (prisonVisitRequestRules.ruleName) {
    PrisonVisitRequestRuleType.VISIT_INTERVAL -> visitIntervalRule
    PrisonVisitRequestRuleType.VISITS_PER_MONTH -> maxVisitsPerMonthRule
    PrisonVisitRequestRuleType.ALREADY_REJECTED_VISIT -> alreadyRejectedVisitRequestRejectionRule
  }
}
