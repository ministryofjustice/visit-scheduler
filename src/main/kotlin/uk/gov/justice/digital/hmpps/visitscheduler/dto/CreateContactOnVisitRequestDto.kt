package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

data class CreateContactOnVisitRequestDto(
  @Schema(description = "Contact Name", example = "John Smith", required = true)
  @field:NotBlank
  @field:Size(min = 3)
  val name: String,

  @Schema(description = "Contact Phone Number", example = "01234 567890", required = true)
  @field:NotBlank
  @field:Size(min = 11)
  val telephone: String
)
