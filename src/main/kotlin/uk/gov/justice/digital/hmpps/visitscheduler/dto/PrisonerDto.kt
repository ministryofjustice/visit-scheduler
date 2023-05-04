package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevels

@Schema(description = "Prisoner information")
data class PrisonerDto(

  @Schema(description = "Prisoner Category", example = "C")
  val category: String? = null,

  @Schema(description = "enhanced privilege", example = "true", required = true)
  val incentiveLevel: IncentiveLevels?,
)
