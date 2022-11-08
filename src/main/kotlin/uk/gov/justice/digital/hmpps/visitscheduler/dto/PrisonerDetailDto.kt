package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
open class PrisonerDetailDto(
  val offenderNo: String,

  val internalLocation: String
)
