package uk.gov.justice.digital.hmpps.visitscheduler.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Visit id", example = "123", required = true) val id: Long,
  @Schema(description = "prisonerId", example = "AF34567G", required = true) val prisonerId: String,
  @Schema(description = "prisonId", example = "MDI", required = true) val prisonId: String,
  @Schema(description = "visitRoom", example = "A1 L3", required = true) val visitRoom: String,
  @Schema(description = "visitType", example = "STANDARD_SOCIAL", required = true) val visitType: String,
  @Schema(description = "visitTypeDescription", example = "Standard Social", required = true) val visitTypeDescription: String,
  @Schema(description = "status", example = "RESERVED", required = true) val status: String,
  @Schema(description = "statusDescription", example = "Reserved", required = true) val statusDescription: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val startTimestamp: LocalDateTime,
  @Schema(
    description = "The finishing date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val endTimestamp: LocalDateTime,
  @Schema(description = "list of visitors associated with the visit", required = false) val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "The id of the session template associated with this visit", example = "123", required = false) val sessionId: Long?,
) {

  constructor(visitEntity: Visit) : this(
    id = visitEntity.id,
    prisonerId = visitEntity.prisonerId,
    prisonId = visitEntity.prisonId,
    startTimestamp = visitEntity.visitStart,
    endTimestamp = visitEntity.visitEnd,
    status = visitEntity.status.name,
    statusDescription = visitEntity.status.description,
    visitRoom = visitEntity.visitRoom,
    visitType = visitEntity.visitType.name,
    visitTypeDescription = visitEntity.visitType.description,
    sessionId = visitEntity.sessionTemplateId,
    visitors = visitEntity.visitors.map { VisitorDto(it) }
  )
}
