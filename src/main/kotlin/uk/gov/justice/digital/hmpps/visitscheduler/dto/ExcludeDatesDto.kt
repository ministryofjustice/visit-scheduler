package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class ExcludeDatesDto(
  @Schema(description = "prison code", example = "BHI", required = true)
  val excludeDate: LocalDate,

  @Schema(description = "add / remove", example = "ADD", required = true)
  val action: UpdateAction,
)
