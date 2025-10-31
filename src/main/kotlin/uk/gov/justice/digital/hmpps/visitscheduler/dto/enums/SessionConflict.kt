package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class SessionConflict {
  NON_ASSOCIATION,
  DOUBLE_BOOKING_OR_RESERVATION,
  SESSION_DATE_BLOCKED,
  PRISON_DATE_BLOCKED,
  VO_NOT_AVAILABLE_FOR_SESSION,
  PVO_NOT_AVAILABLE_FOR_SESSION,
}
