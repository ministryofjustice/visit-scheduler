package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class UnFlagEventReason(val desc: String) {
  VISIT_CANCELLED("visit-cancelled"),
  VISIT_DATE_UPDATED("visit-date-updated"),
  PRISON_EXCLUDE_DATE_REMOVED("prison-exclude-date-removed"),
  NON_ASSOCIATION_REMOVED("non-association-removed"),
  IGNORE_VISIT_NOTIFICATIONS("do-not-change"),
  PRISONER_RETURNED_TO_PRISON("prisoner-returned"),
}
