package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.Valid
import javax.validation.constraints.NotBlank

data class CancelVisitDto(
  @Schema(description = "Outcome - status and text", required = true)
  @field:Valid
  val cancelOutcome: OutcomeDto,

  @Schema(description = "Username for user who actioned this request", required = true)
  @field:NotBlank
  val actionedBy: String,
)
