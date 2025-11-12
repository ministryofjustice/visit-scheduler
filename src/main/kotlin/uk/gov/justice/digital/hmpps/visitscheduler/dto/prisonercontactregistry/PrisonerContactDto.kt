package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A contact for a prisoner")
data class PrisonerContactDto(
  @param:Schema(description = "Identifier for this contact (Person in NOMIS)", example = "5871791") val personId: Long? = null,
)
