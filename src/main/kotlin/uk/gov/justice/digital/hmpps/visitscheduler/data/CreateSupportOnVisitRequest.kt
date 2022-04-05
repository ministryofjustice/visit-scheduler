package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateSupportOnVisitRequest(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null
)
