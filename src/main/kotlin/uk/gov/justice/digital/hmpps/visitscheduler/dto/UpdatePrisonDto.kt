package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min

@Schema(description = "Prison update dto")
data class UpdatePrisonDto(
  @Schema(description = "minimum number of days notice from the current date to booked a visit", example = "2", required = false)
  @field:Min(0)
  val policyNoticeDaysMin: Int?,
  @Schema(description = "maximum number of days notice from the current date to booked a visit", example = "28", required = false)
  @field:Min(0)
  val policyNoticeDaysMax: Int?,
  @Schema(description = "Max number of total visitors")
  @field:Min(1)
  val maxTotalVisitors: Int?,
  @Schema(description = "Max number of adults")
  @field:Min(1)
  val maxAdultVisitors: Int?,
  @Schema(description = "Max number of children, if -1 then no limit is applied")
  @field:Min(-1)
  val maxChildVisitors: Int?,
  @Schema(description = "Age of adults in years")
  @field:Min(10)
  val adultAgeYears: Int?,
)
