package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class SessionConflict {
  NON_ASSOCIATION,
  DOUBLE_BOOKING_OR_RESERVATION,
  SESSION_DATE_BLOCKED,
  PRISON_DATE_BLOCKED,
  REMAND_VISITS_LIMIT_REACHED,
  NO_VOS,
  NO_PVOS,
  NO_VO_OR_PVOS,
}
