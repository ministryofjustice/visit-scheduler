package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateSupportOnVisitRequestDto(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null
)
