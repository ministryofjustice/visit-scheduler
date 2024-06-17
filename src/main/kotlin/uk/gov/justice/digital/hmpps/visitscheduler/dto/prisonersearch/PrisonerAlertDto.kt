package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner alerts")
data class PrisonerAlertDto(

  @Schema(description = "Alert code", example = "HA", required = true)
  val alertCode: String,

  @Schema(description = "Active flag", example = "true", required = true)
  val active: Boolean,
)
