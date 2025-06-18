package uk.gov.justice.digital.hmpps.visitscheduler.dto.enums

@Suppress("unused")
enum class VisitSubStatus(
  val description: String,
) {
  APPROVED("Approved"),
  AUTO_APPROVED("Auto_Approved"),
  REQUESTED("Requested"),

  REJECTED("Rejected"),
  AUTO_REJECTED("Auto_Rejected"),
  WITHDRAWN("Withdrawn"),
  CANCELLED("Cancelled"),
}
