package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Booking

@Schema(description = "Booking")
data class BookingDto(

  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true)
  val prisonId: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "RESERVED", required = true)
  val visitStatus: StatusType,
  @Schema(description = "Outcome Status", example = "VISITOR_CANCELLED", required = false)
  val outcomeStatus: OutcomeStatus? = null,
  @Schema(description = "Visit Notes", required = false)
  val visitNotes: List<NoteDto> = listOf(),
  @Schema(description = "Contact associated with the visit", required = false)
  val visitContact: ContactDto? = null,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: List<SupportDto> = listOf(),

) {
  constructor(entity: Booking) : this(
    prisonerId = entity.prisonerId,
    prisonId = entity.prisonId,
    visitStatus = entity.visitStatus,
    outcomeStatus = entity.outcomeStatus,
    visitType = entity.visitType,
    visitNotes = entity.visitNotes.map { NoteDto(type = it.type, text = it.text) },
    visitContact = entity.visitContact?.let { ContactDto(name = it.name, telephone = it.telephone) },
    visitors = entity.visitors.map { VisitorDto(nomisPersonId = it.nomisPersonId) },
    visitorSupport = entity.support.map { SupportDto(type = it.type, text = it.text) },
  )
}
