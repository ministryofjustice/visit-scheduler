package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateSupportOnVisitRequest(
  @Schema(description = "Support name", example = "OTHER", required = true) @field:NotBlank val supportName: String,
  @Schema(description = "Support details", example = "visually impaired assistance", required = false) val supportDetails: String? = null
)
