package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A contact for a prisoner")
data class RestrictionDto(
  @param:Schema(description = "Restriction Id", example = "123", required = true) val restrictionId: Int,
  @param:Schema(description = "Restriction Type Code", example = "123", required = true) val restrictionType: String,
)
