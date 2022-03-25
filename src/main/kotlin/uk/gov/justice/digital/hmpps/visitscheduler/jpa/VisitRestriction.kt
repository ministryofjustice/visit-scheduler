package uk.gov.justice.digital.hmpps.visitscheduler.jpa

@Suppress("unused")
enum class VisitRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
}
