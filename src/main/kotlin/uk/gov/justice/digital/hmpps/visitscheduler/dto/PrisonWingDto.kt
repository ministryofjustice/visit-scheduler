package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.prison.PrisonWing

@Schema(description = "Prison Wing")
class PrisonWingDto(
  @Schema(description = "Prison wing name", example = "B", required = true)
  val name: String,
) {
  constructor(prisonWing: PrisonWing) : this(
    name = prisonWing.name,
  )
}
