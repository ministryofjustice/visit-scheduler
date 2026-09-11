package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class PrisonVisitRequestRuleType(val config: List<PrisonVisitRequestRuleConfigType>, val ruleType: RequestRuleType) {
  VISIT_INTERVAL(listOf(PrisonVisitRequestRuleConfigType.INTERVAL_DAYS_BEFORE_AND_AFTER, PrisonVisitRequestRuleConfigType.VISITS_ALLOWED), RequestRuleType.REQUESTED_RULE),
  VISITS_PER_MONTH(listOf(PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH), RequestRuleType.REQUESTED_RULE),
  ALREADY_REJECTED_VISIT(listOf(PrisonVisitRequestRuleConfigType.REJECTION_INTERVAL_IN_HOURS, PrisonVisitRequestRuleConfigType.TOTAL_REJECTIONS), RequestRuleType.REJECTION_RULE),
}

enum class RequestRuleType {
  REQUESTED_RULE,
  REJECTION_RULE,
}
