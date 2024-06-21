package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }
  fun build(visitEntity: Visit): VisitDto {
    val applicationReference = getApplicationReference(visitEntity)

    return VisitDto(
      applicationReference = applicationReference,
      reference = visitEntity.reference,
      prisonerId = visitEntity.prisonerId,
      prisonCode = visitEntity.prison.code,
      visitRoom = visitEntity.visitRoom,
      visitStatus = visitEntity.visitStatus,
      outcomeStatus = visitEntity.outcomeStatus,
      visitType = visitEntity.visitType,
      visitRestriction = visitEntity.visitRestriction,
      startTimestamp = visitEntity.sessionSlot.slotStart,
      endTimestamp = visitEntity.sessionSlot.slotEnd,
      visitNotes = visitEntity.visitNotes.map { VisitNoteDto(it) },
      visitContact = ContactDto(visitEntity.visitContact!!),
      visitors = visitEntity.visitors.map { VisitorDto(it) },
      visitorSupport = visitEntity.support?.let { VisitorSupportDto(it) },
      createdTimestamp = visitEntity.createTimestamp ?: LocalDateTime.now(),
      modifiedTimestamp = visitEntity.modifyTimestamp ?: LocalDateTime.now(),
      sessionTemplateReference = visitEntity.sessionSlot.sessionTemplateReference,
      userType = visitEntity.userType,
    )
  }

  private fun getApplicationReference(
    visitEntity: Visit,
  ): String {
    val application = visitEntity.getLastCompletedApplication()
    return application?.reference ?: run {
      // This catches an issues when two requests from the booking occur at the same time see bookVisit method in visit service
      // This happens when a double booking happens at the same time it is an edge case.
      LOG.error("Visit ${visitEntity.reference} should have a completed application")
      "invalidReference"
    }
  }
}
