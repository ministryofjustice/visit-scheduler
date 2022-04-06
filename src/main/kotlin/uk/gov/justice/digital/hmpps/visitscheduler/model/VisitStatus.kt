package uk.gov.justice.digital.hmpps.visitscheduler.model

@Suppress("unused")
enum class VisitStatus(
  val description: String,
) {
  RESERVED("Reserved"),
  BOOKED("Booked"),
  CANCELLED("Cancelled")
}
