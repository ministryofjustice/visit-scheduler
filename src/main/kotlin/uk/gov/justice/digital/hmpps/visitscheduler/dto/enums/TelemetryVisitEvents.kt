package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class TelemetryVisitEvents(val eventName: String) {
  VISIT_SLOT_RESERVED_EVENT("visit-slot-reserved"),
  VISIT_CHANGED_EVENT("visit-changed"),
  VISIT_BOOKED_EVENT("visit-booked"),
  APPLICATION_SLOT_CHANGED_EVENT("application-slot-changed"),
  VISIT_CANCELLED_EVENT("visit-cancelled"),
  APPLICATION_DELETED_EVENT("visit-slot-released"),
  VISIT_MIGRATED_EVENT("visit-migrated"),
  CANCELLED_VISIT_MIGRATED_EVENT("cancelled-visit-migrated"),
  ACCESS_DENIED_ERROR_EVENT("visit-access-denied-error"),
  INTERNAL_SERVER_ERROR_EVENT("visit-internal-server-error"),
  BAD_REQUEST_ERROR_EVENT("visit-bad-request-error"),
  PUBLISH_ERROR_EVENT("visit-publish-event-error"),
  FLAGGED_VISIT_EVENT("flagged-visit-event"),
  UNFLAGGED_VISIT_EVENT("unflagged-visit-event"),

  // exclude dates
  ADD_EXCLUDE_DATE_EVENT("add-exclude-date"),
  REMOVE_EXCLUDE_DATE_EVENT("remove-exclude-date"),

  // reporting
  VISIT_COUNTS_REPORT("visit-counts-report"),
}
