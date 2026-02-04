package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class PrisonVisitRequestRuleType(config: List<PrisonVisitRequestRuleConfigType>) {
  VISIT_INTERVAL(listOf(PrisonVisitRequestRuleConfigType.NUMBER_OF_DAYS)),
  VISITS_PER_MONTH(listOf(PrisonVisitRequestRuleConfigType.MAX_VISITS_PER_MONTH)),
}
