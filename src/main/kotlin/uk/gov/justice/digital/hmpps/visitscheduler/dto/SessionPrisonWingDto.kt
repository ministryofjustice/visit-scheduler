package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionPrisonWing

@Schema(description = "Prison Wing")
class SessionPrisonWingDto(
  @Schema(description = "Prison wing name", example = "B", required = true)
  val name: String,
) {
  constructor(prisonWing: SessionPrisonWing) : this(
    name = prisonWing.name,
  )
}
