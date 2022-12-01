package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api


import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
data class PrisonerDetailsDto(
  @Schema(description = "The prisoner's nomsId", example = "A0000AA", required = true)
  val nomsId: String,

  @Schema(description = "Establishment Code for prisoner", example = "MDI", required = true)
  val establishmentCode: String,
)
