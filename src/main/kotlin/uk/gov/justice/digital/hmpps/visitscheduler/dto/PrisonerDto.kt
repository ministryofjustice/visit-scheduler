package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel

@Schema(description = "Prisoner information")
data class PrisonerDto(

  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,

  @Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @Schema(description = "enhanced privilege", example = "true", required = true)
  val incentiveLevel: IncentiveLevel? = null,

  @Schema(description = "prison code", example = "BHI", required = true)
  var prisonCode: String? = null,

  @Schema(
    description = "Convicted Status",
    name = "convictedStatus",
    example = "Convicted",
    allowableValues = ["Convicted", "Remand"],
  )
  val convictedStatus: String? = null,
)
