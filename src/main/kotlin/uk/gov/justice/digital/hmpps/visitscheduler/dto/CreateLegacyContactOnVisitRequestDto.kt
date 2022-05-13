package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateLegacyContactOnVisitRequestDto(
  @Schema(description = "Contact Name", example = "John Smith", defaultValue = "UNKNOWN", required = false)
  @field:NotBlank
  val name: String? = "UNKNOWN",
  @Schema(description = "Contact Phone Number", example = "01234 567890", defaultValue = "UNKNOWN", required = false)
  val telephone: String? = "UNKNOWN",
)
