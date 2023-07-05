package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethod

data class CancelVisitDto(
  @Schema(description = "Outcome - status and text", required = true)
  @field:Valid
  val cancelOutcome: OutcomeDto,

  @Schema(description = "Username for user who actioned this request", required = true)
  @field:NotBlank
  val actionedBy: String,

  @Schema(description = "application method", required = true)
  @field:NotNull
  val applicationMethod : ApplicationMethod
)
