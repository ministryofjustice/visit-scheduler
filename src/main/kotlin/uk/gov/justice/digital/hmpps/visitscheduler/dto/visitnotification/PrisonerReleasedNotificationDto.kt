package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PrisonerReleasedNotificationDto(
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val prisonCode: String,
  @NotNull
  val reasonType: ReleaseReasonType,
)
