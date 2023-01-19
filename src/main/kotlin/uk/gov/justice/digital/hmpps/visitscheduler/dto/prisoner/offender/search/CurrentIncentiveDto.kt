package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class CurrentIncentiveDto(
  @Schema(description = "Incentive level code and description", required = true)
  val level: IncentiveLevelDto,

  @Schema(description = "Date time of the incentive", example = "2021-07-05T10:35:17", required = true)
  val dateTime: LocalDateTime?,

  @Schema(description = "Schedule new review date", example = "2022-11-10", required = true)
  val nextReviewDate: LocalDate?,
)
