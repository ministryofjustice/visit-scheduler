package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisoner.offender.search

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class PrisonerIncentiveLevelDto(
  @Schema(description = "Prisoner Number", example = "A1234AA", required = true)
  val prisonerNumber: String,

  @Schema(description = "Incentive level", required = false)
  val currentIncentive: CurrentIncentiveDto?,

  @Schema(description = "Prison ID", example = "MDI", required = false)
  val prisonId: String?
)
