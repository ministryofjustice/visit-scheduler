package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime

@Component
class VisitDtoBuilder {

  fun build(visitEntity: Visit): VisitDto {
    return VisitDto(
      applicationReference = visitEntity.applications.last { it.completed }.reference,
      reference = visitEntity.reference,
      prisonerId = visitEntity.prisonerId,
      prisonCode = visitEntity.prison.code,
      visitRoom = visitEntity.visitRoom,
      visitStatus = visitEntity.visitStatus,
      outcomeStatus = visitEntity.outcomeStatus,
      visitType = visitEntity.visitType,
      visitRestriction = visitEntity.visitRestriction,
      startTimestamp = visitEntity.sessionSlot.slotDate.atTime(visitEntity.sessionSlot.slotTime),
      endTimestamp = visitEntity.sessionSlot.slotDate.atTime(visitEntity.sessionSlot.slotEndTime),
      visitNotes = visitEntity.visitNotes.map { VisitNoteDto(it) },
      visitContact = ContactDto(visitEntity.visitContact),
      visitors = visitEntity.visitors.map { VisitorDto(it) },
      visitorSupport = visitEntity.support.map { VisitorSupportDto(it) },
      createdTimestamp = visitEntity.createTimestamp ?: LocalDateTime.now(),
      modifiedTimestamp = visitEntity.modifyTimestamp ?: LocalDateTime.now(),
      sessionTemplateReference = visitEntity.sessionSlot.sessionTemplateReference,
    )
  }
}
