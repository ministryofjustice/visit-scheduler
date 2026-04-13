package uk.gov.justice.digital.hmpps.visitscheduler.dto.relationships

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Restriction related to a specific relationship between a prisoner and contact")
data class PrisonerContactRestrictionsResponse(
  @Schema(description = "Relationship specific restrictions")
  val prisonerContactRestrictions: List<ContactRestrictionDto>,
  @Schema(description = "Global (estate-wide) restrictions for the contact")
  val contactGlobalRestrictions: List<ContactRestrictionDto>,
)
