package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime

data class VisitPreviewDto(
  @param:Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  @field:NotNull
  val prisonerId: String,

  @param:Schema(description = "First name of the prisoner", example = "John", required = true)
  @field:NotNull
  val firstName: String,

  @param:Schema(description = "Last name of the prisoner", example = "Smith", required = true)
  @field:NotNull
  val lastName: String,

  @param:Schema(description = "Visit reference", example = "dp-we-rs-te", required = true)
  @field:NotNull
  val visitReference: String,

  @param:Schema(description = "Number of visitors added to the visit", example = "10", required = true)
  @field:NotNull
  val visitorCount: Int,

  @param:Schema(description = "Timeslot for the visit", required = true)
  @field:NotNull
  val visitTimeSlot: SessionTimeSlotDto,

  @param:Schema(description = "Date the visit was first booked or migrated", example = "2018-12-01T13:45:00", required = true)
  val firstBookedDateTime: LocalDateTime,

  @param:Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: VisitRestriction,

  @param:Schema(description = "Visit Status", example = "BOOKED", required = true)
  val visitStatus: VisitStatus,

  @param:Schema(description = "Visit Sub Status", example = "REQUESTED", required = true)
  val visitSubStatus: VisitSubStatus,
) {
  constructor(visit: Visit, visitFirstBookedDateTime: LocalDateTime?) :
    this(
      prisonerId = visit.prisonerId,
      firstName = visit.prisonerId,
      lastName = visit.prisonerId,
      visitReference = visit.reference,
      visitorCount = visit.visitors.size,
      visitTimeSlot = SessionTimeSlotDto(visit.sessionSlot.slotStart.toLocalTime(), visit.sessionSlot.slotEnd.toLocalTime()),
      firstBookedDateTime = visitFirstBookedDateTime ?: visit.createTimestamp ?: LocalDateTime.now(),
      visitRestriction = visit.visitRestriction,
      visitStatus = visit.visitStatus,
      visitSubStatus = visit.visitSubStatus,
    )
}
