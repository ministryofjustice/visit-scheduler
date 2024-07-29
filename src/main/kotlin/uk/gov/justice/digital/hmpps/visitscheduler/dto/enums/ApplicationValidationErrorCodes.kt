package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class ApplicationValidationErrorCodes(
  val description: String,
) {
  APPLICATION_INVALID_PRISONER_NOT_FOUND("Prisoner not found"),
  APPLICATION_INVALID_PRISON_PRISONER_MISMATCH("Application prison code - does not match prisoner's prison code"),
  APPLICATION_INVALID_SESSION_NOT_AVAILABLE("Session not available to prisoner"),
  APPLICATION_INVALID_SESSION_TEMPLATE_NOT_FOUND("Session template not found"),
  APPLICATION_INVALID_NON_ASSOCIATION_VISITS("Non association has clashing visits"),
  APPLICATION_INVALID_VISIT_ALREADY_BOOKED("Visit already booked for prisoner."),
  APPLICATION_INVALID_NO_VO_BALANCE("No VO balance to book visit."),
  APPLICATION_INVALID_NO_SLOT_CAPACITY("Not enough slots available."),
}
