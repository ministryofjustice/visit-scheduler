package uk.gov.justice.digital.hmpps.visitscheduler.model

@Suppress("unused")
enum class VisitStatus(
  val description: String,
) {
  BOOKED("Booked"),
  CANCELLED("Cancelled"),
}
