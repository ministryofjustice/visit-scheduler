package uk.gov.justice.digital.hmpps.visitscheduler.dto.visit

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.NoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.SupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
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
  val visitStatus: StatusType,
  @Schema(description = "Outcome Status", example = "VISITOR_CANCELLED", required = false)
  val outcomeStatus: OutcomeStatus? = null,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: RestrictionType,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Notes", required = false)
  val visitNotes: List<NoteDto> = listOf(),
  @Schema(description = "Contact associated with the visit", required = false)
  val visitContact: ContactDto? = null,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: List<SupportDto> = listOf(),
  @Schema(description = "The visit created date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val createdTimestamp: LocalDateTime,
  @Schema(description = "The visit modified date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val modifiedTimestamp: LocalDateTime,

) {
  constructor(entity: Reservation) : this(
    reference = entity.reference,
    visitRoom = entity.visitRoom,
    startTimestamp = entity.visitStart,
    endTimestamp = entity.visitEnd,
    createdTimestamp = entity.createTimestamp!!,
    modifiedTimestamp = entity.modifyTimestamp!!,

    prisonerId = entity.booking?.prisonerId ?: "",
    prisonId = entity.booking?.prisonId ?: "",
    visitStatus = entity.booking?.visitStatus ?: StatusType.RESERVED,
    outcomeStatus = entity.booking?.outcomeStatus,
    visitType = entity.booking?.visitType ?: VisitType.SOCIAL,
    visitRestriction = entity.visitRestriction,
    visitNotes = entity.booking?.visitNotes?.map { NoteDto(it) } ?: listOf(),
    visitContact = entity.booking?.visitContact?.let { ContactDto(it) },
    visitors = entity.booking?.visitors?.map { VisitorDto(it) } ?: listOf(),
    visitorSupport = entity.booking?.support?.map { SupportDto(it) } ?: listOf(),
  )
}
