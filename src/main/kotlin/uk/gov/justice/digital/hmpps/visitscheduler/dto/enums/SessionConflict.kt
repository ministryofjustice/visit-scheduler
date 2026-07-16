package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class SessionConflict {
  NON_ASSOCIATION,
  DOUBLE_BOOKING_OR_RESERVATION,
  SESSION_DATE_BLOCKED,
  PRISON_DATE_BLOCKED,
  REMAND_VISITS_LIMIT_REACHED,
  NO_VO_BALANCE,
  NO_PVO_BALANCE,
  NO_VO_OR_PVO_BALANCE,
}
