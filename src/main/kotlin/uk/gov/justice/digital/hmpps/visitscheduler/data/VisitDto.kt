package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Visit id", example = "123", required = true) val id: Long,
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true) val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true) val prisonId: String,
  @Schema(description = "Visit Room", example = "A1 L3", required = true) val visitRoom: String,
  @Schema(description = "Visit Type", example = "STANDARD_SOCIAL", required = true) val visitType: String,
  @Schema(description = "Visit Type Description", example = "Standard Social", required = true) val visitTypeDescription: String,
  @Schema(description = "Visit Status", example = "RESERVED", required = true) val visitStatus: String,
  @Schema(description = "Visit Status Description", example = "Reserved", required = true) val visitStatusDescription: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @field:NotBlank val startTimestamp: LocalDateTime,
  @Schema(
    description = "The finishing date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @field:NotBlank val endTimestamp: LocalDateTime,
  @Schema(description = "Reasonable Adjustments", required = false) val reasonableAdjustments: String? = null,
  @Schema(description = "Main Contact associated with the visit", required = false) val mainContact: ContactDto? = null,
  @Schema(description = "List of visitors associated with the visit", required = false) val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "Session Id identifying the visit session template", example = "123", required = false) val sessionId: Long? = null,
) {

  constructor(visitEntity: Visit) : this(
    id = visitEntity.id,
    prisonerId = visitEntity.prisonerId,
    prisonId = visitEntity.prisonId,
    startTimestamp = visitEntity.visitStart,
    endTimestamp = visitEntity.visitEnd,
    visitStatus = visitEntity.status.name,
    visitStatusDescription = visitEntity.status.description,
    visitRoom = visitEntity.visitRoom,
    reasonableAdjustments = visitEntity.reasonableAdjustments,
    mainContact = visitEntity.mainContact?.let { ContactDto(it) },
    visitType = visitEntity.visitType.name,
    visitTypeDescription = visitEntity.visitType.description,
    sessionId = visitEntity.sessionTemplateId,
    visitors = visitEntity.visitors.map { VisitorDto(it) }
  )
}
