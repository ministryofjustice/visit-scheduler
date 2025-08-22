package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class UnFlagEventReason(val desc: String) {
  VISIT_CANCELLED("visit-cancelled"),
  VISIT_CANCELLED_ON_NOMIS("visit-cancelled-on-nomis"),
  REQUESTED_VISIT_WITHDRAWN("requested-visit-withdrawn"),
  VISIT_UPDATED("visit-updated"),
  VISIT_REQUEST_APPROVED("visit-request-approved"),
  VISIT_REQUEST_REJECTED("visit-request-rejected"),
  VISIT_REQUEST_AUTO_REJECTED("visit-request-auto-rejected"),
  PRISON_EXCLUDE_DATE_REMOVED("prison-exclude-date-removed"),
  SESSION_EXCLUDE_DATE_REMOVED("session-exclude-date-removed"),
  NON_ASSOCIATION_REMOVED("non-association-removed"),
  IGNORE_VISIT_NOTIFICATIONS("do-not-change"),
  PRISONER_RETURNED_TO_PRISON("prisoner-returned"),
  PRISONER_ALERT_CODE_REMOVED("prison-alert-code-removed"),
  VISITOR_APPROVED("visitor-approved"),
  NON_ASSOCIATION_VISIT_CANCELLED("non-association-visit-cancelled"),
  NON_ASSOCIATION_VISIT_UPDATED("non-association-visit-updated"),
  NON_ASSOCIATION_VISIT_IGNORED("non-association-visit-ignored"),
  PAIRED_VISIT_CANCELLED_IGNORED_OR_UPDATED("paired-visit-cancelled-or-ignored-or-updated"),
  COURT_VIDEO_APPOINTMENT_CANCELLED_OR_DELETED("court-video-appointment-cancelled-or-deleted"),
}
