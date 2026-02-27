package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class VisitorApprovedUnapprovedNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,

  @field:NotBlank
  val visitorId: String,
)
