package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class SessionTemplateVisitStatsDto(

  @Schema(description = "Minimum Session Capacity", required = true)
  @field:NotNull
  val minimumCapacity: SessionCapacityDto,

  @Schema(description = "visit count for given date", example = "10", required = true)
  @field:Min(0)
  val visitCount: Int,
)
