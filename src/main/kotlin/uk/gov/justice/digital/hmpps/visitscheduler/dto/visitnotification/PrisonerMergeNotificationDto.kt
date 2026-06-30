package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class PrisonerMergeNotificationDto(
  @field:NotBlank
  @param:Schema(description = "New Prisoner Number post merge", example = "A1234BC", required = true)
  val newPrisonerNumber: String,

  @field:NotBlank
  @param:Schema(description = "Removed Prisoner Number post merge", example = "A5678YZ", required = true)
  val oldPrisonerNumber: String,
)
