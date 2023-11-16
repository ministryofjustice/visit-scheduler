package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionTemplateService
import java.time.LocalDateTime

@Component
class VisitDtoBuilder {

  @Autowired
  private lateinit var sessionTemplateService: SessionTemplateService

  fun build(visitEntity: Visit): VisitDto {
    val timeSlot = sessionTemplateService.getSessionTimeSlotDto(visitEntity.sessionTemplateReference)

    return VisitDto(
      applicationReference = visitEntity.applicationReference,
      reference = visitEntity.reference,
      prisonerId = visitEntity.prisonerId,
      prisonCode = visitEntity.prison.code,
      visitRoom = visitEntity.visitRoom,
      visitStatus = visitEntity.visitStatus,
      outcomeStatus = visitEntity.outcomeStatus,
      visitType = visitEntity.visitType,
      visitRestriction = visitEntity.visitRestriction,
      startTimestamp = timeSlot?.let { visitEntity.visitStart.toLocalDate().atTime(it.startTime) } ?: visitEntity.visitStart,
      endTimestamp = timeSlot?.let { visitEntity.visitEnd.toLocalDate().atTime(it.endTime) } ?: visitEntity.visitEnd,
      visitNotes = visitEntity.visitNotes.map { VisitNoteDto(it) },
      visitContact = visitEntity.visitContact?.let { ContactDto(it) },
      visitors = visitEntity.visitors.map { VisitorDto(it) },
      visitorSupport = visitEntity.support.map { VisitorSupportDto(it) },
      createdTimestamp = visitEntity.createTimestamp ?: LocalDateTime.now(),
      modifiedTimestamp = visitEntity.modifyTimestamp ?: LocalDateTime.now(),
      sessionTemplateReference = visitEntity.sessionTemplateReference,
    )
  }
}
