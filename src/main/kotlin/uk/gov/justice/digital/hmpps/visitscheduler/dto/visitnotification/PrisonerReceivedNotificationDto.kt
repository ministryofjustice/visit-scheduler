package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType

data class PrisonerReceivedNotificationDto(
  @NotBlank
  val prisonerNumber: String,

  @NotBlank
  val reason: PrisonerReceivedReasonType,

  @NotBlank
  val prisonCode: String,
)
