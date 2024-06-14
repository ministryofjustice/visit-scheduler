package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerAlertDto

@Schema(description = "Prisoner information")
data class PrisonerDto(

  @Schema(description = "Prisoner Category", example = "C")
  val prisonerId: String,

  @Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @Schema(description = "enhanced privilege", example = "true", required = true)
  val incentiveLevel: IncentiveLevel? = null,

  @Schema(description = "prison code", example = "BHI", required = true)
  var prisonCode: String? = null,

  @Schema(description = "Prisoner Alerts", required = false)
  val alerts: List<PrisonerAlertDto> = emptyList(),
)
