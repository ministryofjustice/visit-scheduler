package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class PrisonerReleasedNotificationDto(
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val prisonCode: String,
)
