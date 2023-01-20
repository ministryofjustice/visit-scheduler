package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "Current Incentive")
data class CurrentIncentiveDto(
  @Schema(description = "Incentive level code and description", required = true)
  val level: IncentiveLevelDto,

  @Schema(description = "Date time of the incentive", example = "2021-07-05T10:35:17", required = true)
  val dateTime: LocalDateTime,

  @Schema(description = "Schedule new review date", example = "2022-11-10", required = false)
  val nextReviewDate: LocalDate? = null,
)

@Schema(description = "incentive level - code and description")
data class IncentiveLevelDto(
  @Schema(description = "Incentive level - code", example = "STD", required = false)
  val code: String?,

  @Schema(description = "Incentive level - description", example = "Standard", required = true)
  val description: String,
)
