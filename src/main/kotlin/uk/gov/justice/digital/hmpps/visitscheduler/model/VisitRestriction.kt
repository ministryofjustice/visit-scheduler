package uk.gov.justice.digital.hmpps.visitscheduler.model

@Suppress("unused")
enum class VisitRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
  UNKNOWN("Unknown"),
}
