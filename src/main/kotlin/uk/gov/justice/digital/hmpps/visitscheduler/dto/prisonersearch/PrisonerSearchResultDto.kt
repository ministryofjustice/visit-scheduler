package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner information")
data class PrisonerSearchResultDto(
  @param:Schema(description = "Prisoner Number", example = "A1234AA", required = true)
  val prisonerNumber: String? = null,

  @param:Schema(required = true, description = "First Name", example = "Robert")
  val firstName: String,

  @param:Schema(required = true, description = "Last name", example = "Larsen")
  val lastName: String,

  @param:Schema(description = "Incentive level", required = false)
  val currentIncentive: CurrentIncentiveDto? = null,

  @param:Schema(description = "Prison ID", example = "MDI", required = false)
  val prisonId: String? = null,

  @param:Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @param:Schema(
    description = "Convicted Status",
    name = "convictedStatus",
    example = "Convicted",
    allowableValues = ["Convicted", "Remand"],
  )
  val convictedStatus: String? = null,
)
