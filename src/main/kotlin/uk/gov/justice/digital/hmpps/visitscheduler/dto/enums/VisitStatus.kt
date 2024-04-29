package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class VisitStatus(
  val description: String,
) {
  BOOKED("Booked"),
  CANCELLED("Cancelled"),
}
