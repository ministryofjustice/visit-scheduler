package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class EventAuditType {
  RESERVED_VISIT,
  CHANGING_VISIT,
  MIGRATED_VISIT,
  BOOKED_VISIT,
  UPDATED_VISIT,
  CANCELLED_VISIT,
  NON_ASSOCIATION_EVENT,
  PRISONER_RELEASED_EVENT,
  PRISONER_RECEIVED_EVENT,
  PRISONER_RESTRICTION_CHANGE_EVENT,
  PRISONER_ALERTS_UPDATED_EVENT,
  PRISON_VISITS_BLOCKED_FOR_DATE,
  IGNORE_VISIT_NOTIFICATIONS_EVENT,
  PERSON_RESTRICTION_UPSERTED_EVENT,
  PERSON_RESTRICTION_DELETED_EVENT,
}
