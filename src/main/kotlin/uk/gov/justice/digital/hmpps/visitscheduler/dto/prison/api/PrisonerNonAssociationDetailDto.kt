package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner non-association detail")
class PrisonerNonAssociationDetailDto(
  @param:Schema(description = "The prisoner with whom not to associate.", required = true)
  val otherPrisonerDetails: OtherPrisonerDetails,
)
