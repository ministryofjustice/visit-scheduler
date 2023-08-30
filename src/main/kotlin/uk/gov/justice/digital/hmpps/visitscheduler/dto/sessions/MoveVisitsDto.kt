package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class MoveVisitsDto(
  @Schema(description = "Session template reference for session template from which booked / reserved visits need to be moved.", example = "v9d.7ed.7u", required = true)
  @field:NotBlank
  val fromSessionTemplateReference: String,

  @Schema(description = "Session template reference for session template to which booked / reserved visits need to be moved.", example = "v9d.7ed.5e", required = true)
  @field:NotBlank
  val toSessionTemplateReference: String,

  @Schema(description = "Date from which booked / reserved visits need to be moved .", example = "2023-09-01", required = true)
  @field:FutureOrPresent
  val fromDate: LocalDate,
)
