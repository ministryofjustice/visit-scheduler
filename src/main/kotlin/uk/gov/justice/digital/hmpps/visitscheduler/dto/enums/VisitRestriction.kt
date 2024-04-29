package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class VisitRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
  UNKNOWN("Unknown"),
}
