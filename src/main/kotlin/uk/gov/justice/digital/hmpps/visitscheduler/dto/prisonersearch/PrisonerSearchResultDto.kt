package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner information")
data class PrisonerSearchResultDto(
  @Schema(description = "Prisoner Number", example = "A1234AA", required = true)
  val prisonerNumber: String? = null,

  @Schema(required = true, description = "First Name", example = "Robert")
  val firstName: String,

  @Schema(required = true, description = "Last name", example = "Larsen")
  val lastName: String,

  @Schema(description = "Incentive level", required = false)
  val currentIncentive: CurrentIncentiveDto? = null,

  @Schema(description = "Prison ID", example = "MDI", required = false)
  val prisonId: String? = null,

  @Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @Schema(
    description = "Convicted Status",
    name = "convictedStatus",
    example = "Convicted",
    allowableValues = ["Convicted", "Remand"],
  )
  val convictedStatus: String? = null,
)
