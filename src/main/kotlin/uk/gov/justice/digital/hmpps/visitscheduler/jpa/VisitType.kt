package uk.gov.justice.digital.hmpps.visitscheduler.jpa

@Suppress("unused")
enum class VisitType(
  val description: String,
) {
  SOCIAL("Social"),
  OFFICIAL("Official"),
  FAMILY("Family"),
}
