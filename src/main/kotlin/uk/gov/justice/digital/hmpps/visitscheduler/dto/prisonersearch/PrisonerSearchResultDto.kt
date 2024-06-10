package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import com.fasterxml.jackson.annotation.JsonProperty
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

  @JsonProperty("lastPrisonId")
  @Schema(description = "Last Prison Code", example = "MDI", required = false)
  val lastPrisonCode: String? = null,
)
