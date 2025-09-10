package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

enum class AutoRejectionReason(
  val description: String,
) {
  MINIMUM_BOOKING_WINDOW_REACHED("Auto rejected by minimum booking window system cron"),
  PRISONER_RELEASED("Auto rejected by prisoner released event"),
  PRISONER_TRANSFERRED("Auto rejected by prisoner received event (reason prisoner transferred)"),
}
