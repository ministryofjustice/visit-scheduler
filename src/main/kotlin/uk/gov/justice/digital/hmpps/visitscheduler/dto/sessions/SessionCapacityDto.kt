package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@Schema(description = "Session Capacity")
data class SessionCapacityDto(
  @Schema(description = "closed capacity", example = "10", required = true)
  @field:Min(0)
  val closed: Int,
  @Schema(description = "open capacity", example = "50", required = true)
  @field:Min(0)
  val open: Int,
) {
  constructor(sessionTemplateEntity: SessionTemplate) : this(
    closed = sessionTemplateEntity.closedCapacity,
    open = sessionTemplateEntity.openCapacity,
  )
  constructor(sessionTemplates: List<SessionTemplate>) : this(
    closed = sessionTemplates.sumOf { it.closedCapacity },
    open = sessionTemplates.sumOf { it.openCapacity },
  )

  operator fun plus(sessionCapacityDto: SessionCapacityDto): SessionCapacityDto {
    return SessionCapacityDto(closed = this.closed + sessionCapacityDto.closed, open = this.open + sessionCapacityDto.open)
  }
}
