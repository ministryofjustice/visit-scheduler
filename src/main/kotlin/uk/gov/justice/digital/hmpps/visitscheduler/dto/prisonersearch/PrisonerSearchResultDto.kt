package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner information")
data class PrisonerSearchResultDto(
  @Schema(description = "Prisoner Number", example = "A1234AA", required = true)
  val prisonerNumber: String? = null,

  @Schema(description = "Incentive level", required = false)
  val currentIncentive: CurrentIncentiveDto? = null,

  @Schema(description = "Prison ID", example = "MDI", required = false)
  val prisonId: String? = null,

  @Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @Schema(description = "Prisoner Alerts", required = false)
  val alerts: List<PrisonerAlertDto> = emptyList(),
)
