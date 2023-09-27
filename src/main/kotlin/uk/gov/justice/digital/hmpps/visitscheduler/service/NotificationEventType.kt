package uk.gov.justice.digital.hmpps.visitscheduler.service

enum class NotificationEventType(val reviewType: String) {
  NON_ASSOCIATION_EVENT("Non-association"),
  PRISONER_RELEASED_EVENT("Prisoner-released"),
}
