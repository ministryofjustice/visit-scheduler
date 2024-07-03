package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class SessionTemplateVisitStatsDto(

  @Schema(description = "Minimum Session Capacity", required = true)
  @field:NotNull
  val minimumCapacity: SessionCapacityDto,

  @Schema(description = "booked, reserved or changing visit counts", example = "10", required = true)
  @field:Min(0)
  val visitCount: Int,

  @Schema(description = "cancelled visit counts", example = "10", required = true)
  @field:Min(0)
  val cancelCount: Int,

  @Schema(description = "count of visits by date", required = false)
  val visitsByDate: List<SessionTemplateVisitCountsDto>?,

  @Schema(description = "count of cancelled visits by date", required = false)
  val cancelVisitsByDate: List<SessionTemplateVisitCountsDto>?,
)
