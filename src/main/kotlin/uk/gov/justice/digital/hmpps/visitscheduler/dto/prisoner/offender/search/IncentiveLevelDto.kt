package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class IncentiveLevelDto(
  @Schema(description = "Incentive level - code", example = "STD", required = false)
  val code: String?,

  @Schema(description = "Incentive level - description", example = "Standard", required = true)
  val description: String,
)
