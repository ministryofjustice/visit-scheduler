package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class SessionTemplateVisitCountsDto(
  @Schema(description = "Date when the visits are booked or reserved", example = "2023-07-01", required = true)
  @field:NotNull
  val visitDate: LocalDate,

  @Schema(description = "Open and closed visit counts for the day", required = true)
  @field:NotNull
  val visitCounts: SessionCapacityDto,
)
