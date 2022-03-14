package uk.gov.justice.digital.hmpps.visitscheduler.jpa

@Suppress("unused")
enum class VisitType(
  val description: String,
) {
  STANDARD_SOCIAL("Standard Social"),
  OFFICIAL("Official"),
  FAMILY("Family"),
}
