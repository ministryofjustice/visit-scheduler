package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "prisoner non-association")
class OtherPrisonerDetails(
  @param:Schema(description = "The prisoner number", example = "G0135GA", required = true)
  val prisonerNumber: String,
)
