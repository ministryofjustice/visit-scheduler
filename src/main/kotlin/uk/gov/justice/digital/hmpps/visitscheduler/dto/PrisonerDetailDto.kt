package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
open class PrisonerDetailDto(
  @Schema(description = "The prisoner's unique offender number, naps to prisonerId on VSIP", example = "A0000AA", required = true)
  val offenderNo: String,

  @Schema(description = "Name of the location where the prisoner resides (if in prison)", example = "WRI-B-3-018")
  val internalLocation: String
)
