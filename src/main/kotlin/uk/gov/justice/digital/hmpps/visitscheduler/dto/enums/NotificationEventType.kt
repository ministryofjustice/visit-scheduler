package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class NotificationEventType(val reviewType: String) {
  NON_ASSOCIATION_EVENT("Non-association"),
  PRISONER_RELEASED_EVENT("Prisoner-released"),
  PRISONER_RESTRICTION_CHANGE_EVENT("Prisoner-restriction-change"),
  PRISON_VISITS_BLOCKED_FOR_DATE("Prison-visits-blocked-for-date"),
  PRISONER_RECEIVED_EVENT("Prisoner-received"),
  PRISONER_ALERTS_UPDATED_EVENT("Prisoner-alerts-updated"),
}
