package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@Schema(description = "Session Capacity")
data class SessionCapacityDto(
  @Schema(description = "closed capacity", example = "10", required = true)
  val closed: Int,
  @Schema(description = "open capacity", example = "50", required = true)
  val open: Int,
  @Schema(description = "Capacity group", example = "Main Group", required = true)
  val capacityGroup: String,
) {
  constructor(sessionTemplateEntity: SessionTemplate) : this(
    closed = sessionTemplateEntity.closedCapacity,
    open = sessionTemplateEntity.openCapacity,
    capacityGroup = sessionTemplateEntity.capacityGroup,
  )
  constructor(capacityGroupOfSessionTemplates: List<SessionTemplate>) : this(
    closed = capacityGroupOfSessionTemplates.sumOf { it.closedCapacity },
    open = capacityGroupOfSessionTemplates.sumOf { it.openCapacity },
    capacityGroup = capacityGroupOfSessionTemplates.first().capacityGroup,
  )
}
