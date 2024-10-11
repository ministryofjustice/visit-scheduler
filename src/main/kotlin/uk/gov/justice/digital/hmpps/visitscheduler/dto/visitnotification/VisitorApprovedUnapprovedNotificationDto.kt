package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class VisitorApprovedUnapprovedNotificationDto(
  @NotBlank
  val prisonerNumber: String,

  @NotBlank
  val visitorId: String,
)
