package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class PrisonVisitRequestRuleConfigType {
  INTERVAL_DAYS_BEFORE_AND_AFTER,
  VISITS_ALLOWED,
  MAX_VISITS_PER_MONTH,
  REJECTION_INTERVAL_IN_HOURS,
  TOTAL_REJECTIONS,
}
