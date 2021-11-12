package uk.gov.justice.digital.hmpps.visitscheduler.jpa

enum class VisitStatus(
  val description: String,
) {
  RESERVED("Reserved"),
  BOOKED("Booked"),
  CANCELLED_BY_PRISONER("Cancelled by Prisoner"),
  CANCELLED_BY_VISITOR("Cancelled by Visitor"),
  CANCELLED_BY_PRISON("Cancelled by Prison")
}
