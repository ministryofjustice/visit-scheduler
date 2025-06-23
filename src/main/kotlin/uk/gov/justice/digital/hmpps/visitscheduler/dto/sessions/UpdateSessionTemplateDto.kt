package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid

data class UpdateSessionTemplateDto(
  @Schema(description = "Update session template details", example = "Monday Xmas", required = true)
  @field:Valid
  val updateSessionTemplateDetailsDto: UpdateSessionTemplateDetailsDto,

  @Schema(description = "Validation flag - defaults to true, if false skips validation", required = true)
  val validateRequest: Boolean = true,
)
