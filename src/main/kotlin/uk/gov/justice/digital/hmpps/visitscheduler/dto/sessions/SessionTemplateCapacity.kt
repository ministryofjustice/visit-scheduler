package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min

class SessionTemplateCapacity(
  @Schema(description = "closed capacity", example = "10", required = true)
  @field:Min(0)
  val closedCapacity: Int,

  @Schema(description = "open capacity", example = "50", required = true)
  @field:Min(0)
  val openCapacity: Int,
)
