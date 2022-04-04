package uk.gov.justice.digital.hmpps.visitscheduler.jpa

@Suppress("unused")
enum class VisitStatus(
  val description: String,
) {
  RESERVED("Reserved"),
  BOOKED("Booked"),
  CANCELLED("Cancelled"),
  ATTENDED("Attended")
}
