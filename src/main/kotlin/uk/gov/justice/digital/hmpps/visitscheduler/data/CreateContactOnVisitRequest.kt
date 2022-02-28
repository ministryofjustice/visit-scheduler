package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateContactOnVisitRequest(
  @Schema(description = "Contact Name", example = "John Smith", required = true) @field:NotBlank val contactName: String,
  @Schema(description = "Contact Phone", example = "01234 567890", required = true) @field:NotBlank val contactPhone: String
)
