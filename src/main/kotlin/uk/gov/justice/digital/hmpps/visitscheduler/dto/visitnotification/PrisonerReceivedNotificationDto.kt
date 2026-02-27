package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType

data class PrisonerReceivedNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,
  @field:NotBlank
  val prisonCode: String,
  @param:NotBlank
  val reason: PrisonerReceivedReasonType,
)
