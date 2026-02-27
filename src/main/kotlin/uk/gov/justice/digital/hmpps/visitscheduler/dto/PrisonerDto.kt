package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel

@Schema(description = "Prisoner information")
data class PrisonerDto(

  @param:Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,

  @param:Schema(required = true, description = "First Name", example = "Robert")
  val firstName: String,

  @param:Schema(required = true, description = "Last name", example = "Larsen")
  val lastName: String,

  @param:Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @param:Schema(description = "enhanced privilege", example = "true", required = true)
  val incentiveLevel: IncentiveLevel? = null,

  @param:Schema(description = "prison code", example = "BHI", required = true)
  var prisonCode: String? = null,

  @param:Schema(
    description = "Convicted Status",
    name = "convictedStatus",
    example = "Convicted",
    allowableValues = ["Convicted", "Remand"],
  )
  val convictedStatus: String? = null,
)
