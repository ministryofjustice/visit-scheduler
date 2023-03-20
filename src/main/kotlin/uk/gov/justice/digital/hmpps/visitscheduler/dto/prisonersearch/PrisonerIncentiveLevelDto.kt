package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class PrisonerIncentiveLevelDto(
  @Schema(description = "Prisoner Number", example = "A1234AA", required = true)
  val prisonerNumber: String? = null,

  @Schema(description = "Incentive level", required = false)
  val currentIncentive: CurrentIncentiveDto? = null,

  @Schema(description = "Prison ID", example = "MDI", required = false)
  val prisonId: String? = null,
)
