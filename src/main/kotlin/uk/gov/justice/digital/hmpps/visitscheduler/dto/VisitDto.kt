package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Visit Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true)
  val prisonId: String,
  @Schema(description = "Visit Room", example = "A1 L3", required = true)
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "RESERVED", required = true)
  val visitStatus: VisitStatus,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Notes", required = false)
  val visitNotes: List<VisitNoteDto> = listOf(),
  @Schema(description = "Contact associated with the visit", required = false)
  val visitContact: ContactDto? = null,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: List<VisitorSupportDto> = listOf(),
) {
  constructor(visitEntity: Visit) : this(
    reference = visitEntity.reference,
    prisonerId = visitEntity.prisonerId,
    prisonId = visitEntity.prisonId,
    visitRoom = visitEntity.visitRoom,
    visitStatus = visitEntity.visitStatus,
    visitType = visitEntity.visitType,
    visitRestriction = visitEntity.visitRestriction,
    startTimestamp = visitEntity.visitStart,
    endTimestamp = visitEntity.visitEnd,
    visitNotes = visitEntity.visitNotes.map { VisitNoteDto(it) },
    visitContact = visitEntity.visitContact?.let { ContactDto(it) },
    visitors = visitEntity.visitors.map { VisitorDto(it) },
    visitorSupport = visitEntity.support.map { VisitorSupportDto(it) }
  )
}
