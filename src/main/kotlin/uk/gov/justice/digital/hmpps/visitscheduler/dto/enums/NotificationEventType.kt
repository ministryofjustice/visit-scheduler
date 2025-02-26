package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class NotificationEventType(val reviewType: String, val description: String) {
  NON_ASSOCIATION_EVENT("Non-association", "non-association"),
  PRISONER_RELEASED_EVENT("Prisoner-released", "prisoner released"),
  PRISONER_RESTRICTION_CHANGE_EVENT("Prisoner-restriction-change", "prisoner restriction changed"),
  PRISON_VISITS_BLOCKED_FOR_DATE("Prison-visits-blocked-for-date", "visit date is blocked"),
  SESSION_VISITS_BLOCKED_FOR_DATE("Session-visits-blocked-for-date", "visit session is blocked"),
  PRISONER_RECEIVED_EVENT("Prisoner-received", "prisoner transferred"),
  PRISONER_ALERTS_UPDATED_EVENT("Prisoner-alerts-updated", "prisoner alerts changed"),
  PERSON_RESTRICTION_UPSERTED_EVENT("Person-restriction-upserted", "visitor restriction changed"),
  VISITOR_RESTRICTION_UPSERTED_EVENT("Visitor-restriction-upserted", "visitor restriction changed"),
  VISITOR_UNAPPROVED_EVENT("Visitor-unapproved", "visitor unapproved"),
}
