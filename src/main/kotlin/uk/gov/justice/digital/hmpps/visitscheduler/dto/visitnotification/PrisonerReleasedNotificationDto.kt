package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReleaseReasonType

data class PrisonerReleasedNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,
  @field:NotBlank
  val prisonCode: String,
  @field:NotNull
  val reasonType: PrisonerReleaseReasonType,
)
