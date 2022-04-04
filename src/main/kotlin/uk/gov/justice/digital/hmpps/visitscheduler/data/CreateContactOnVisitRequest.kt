package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateContactOnVisitRequest(
  @Schema(description = "Contact Name", example = "John Smith", required = true)
  @field:NotBlank
  val name: String,
  @Schema(description = "Contact Phone Number", example = "01234 567890", required = true)
  @field:NotBlank
  val telephone: String
)
