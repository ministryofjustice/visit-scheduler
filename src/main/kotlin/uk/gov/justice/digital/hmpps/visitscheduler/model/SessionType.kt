package uk.gov.justice.digital.hmpps.visitscheduler.model

enum class SessionType(
  val description: String,
) {
  OPEN("Open Visit Session"),
  CLOSED("Closed Visit Session"),
}
