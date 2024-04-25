package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class IgnoreVisitNotificationsDto(
  @Schema(description = "Reason why the visit's notifications can be ignored", required = true)
  @field:NotBlank
  val reason: String,

  @Schema(description = "Username for user who actioned this request", required = true)
  @field:NotBlank
  val actionedBy: String,
)
