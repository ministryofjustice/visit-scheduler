package uk.gov.justice.digital.hmpps.visitscheduler.service

enum class TelemetryVisitEvents(val eventName: String) {
  VISIT_SLOT_RESERVED_EVENT("visit-slot-reserved"),
  VISIT_CHANGED_EVENT("visit-changed"),
  VISIT_BOOKED_EVENT("visit-booked"),
  VISIT_SLOT_CHANGED_EVENT("visit-slot-changed"),
  VISIT_CANCELLED_EVENT("visit-cancelled"),
  VISIT_DELETED_EVENT("visit-expired-visits-deleted"),
  VISIT_MIGRATED_EVENT("visit-migrated"),

  @Suppress("KotlinDeprecation")
  @Deprecated("Legacy version - to be removed")
  VISIT_UPDATED_EVENT("visit-updated"),

  SESSION_TEMPLATE_CREATED("session-template-created"),
  SESSION_TEMPLATE_DELETED("session-template-deleted"),

  ACCESS_DENIED_ERROR_EVENT("visit-access-denied-error"),
  INTERNAL_SERVER_ERROR_EVENT("visit-internal-server-error"),
  BAD_REQUEST_ERROR_EVENT("visit-bad-request-error"),
  PUBLISH_ERROR_EVENT("visit-publish-event-error"),
}
