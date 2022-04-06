package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Offender non-association")
class OffenderNonAssociationDto(
  @Schema(description = "The offenders number", example = "G0135GA", required = true)
  val offenderNo: String
)
