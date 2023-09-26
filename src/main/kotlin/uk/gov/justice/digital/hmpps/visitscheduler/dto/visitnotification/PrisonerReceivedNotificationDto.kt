package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class PrisonerReceivedNotificationDto(
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val prisonCode: String,
)
