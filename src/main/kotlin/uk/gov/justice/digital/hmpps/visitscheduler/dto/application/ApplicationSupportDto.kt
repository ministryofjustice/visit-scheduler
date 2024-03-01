package uk.gov.justice.digital.hmpps.visitscheduler.dto.application

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "Visitor support")
data class ApplicationSupportDto(
  @Schema(description = "Support text description, if empty is given then existing support text will be removed", example = "visually impaired assistance", required = true)
  @Size(max = 512)
  val description: String,
)
