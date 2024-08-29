package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Prison exclude date")
data class PrisonExcludeDateDto(
  @Schema(description = "exclude date", required = true)
  var excludeDate: LocalDate,

  @Schema(description = "actioned by", required = true)
  var actionedBy: String,
)
