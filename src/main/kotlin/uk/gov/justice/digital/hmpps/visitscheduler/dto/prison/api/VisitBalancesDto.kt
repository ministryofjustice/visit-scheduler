package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Balances of visit orders and privilege visit orders")
data class VisitBalancesDto(
  @Schema(required = true, description = "Balance of visit orders remaining")
  val remainingVo: Int,

  @Schema(required = true, description = "Balance of privilege visit orders remaining")
  val remainingPvo: Int,
)
