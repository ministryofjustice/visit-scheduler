package uk.gov.justice.digital.hmpps.visitscheduler.service.util

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.SUPERSEDED_CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Component
@Transactional(propagation = REQUIRES_NEW)
class PersistVisitHelper(
  private val visitRepository: VisitRepository,
) {

  fun persistBooking(applicationReference: String): VisitDto {
    val visitToBook = visitRepository.findApplication(applicationReference)
      ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")

    visitRepository.findBookedVisit(visitToBook.reference)?.let { existingBooking ->
      existingBooking.visitStatus = CANCELLED
      existingBooking.outcomeStatus = SUPERSEDED_CANCELLATION
      existingBooking.cancelledBy = visitToBook.createdBy
      visitRepository.saveAndFlush(existingBooking)

      // set the new bookings updated by to current username and set createdBy to existing booking username
      visitToBook.updatedBy = visitToBook.createdBy
      visitToBook.createdBy = existingBooking.createdBy
    }

    visitToBook.visitStatus = VisitStatus.BOOKED
    return VisitDto(visitRepository.saveAndFlush(visitToBook))
  }

  fun persistCancel(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val cancelOutcome = cancelVisitDto.cancelOutcome

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus
    visitEntity.cancelledBy = cancelVisitDto.actionedBy

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    return VisitDto(visitRepository.saveAndFlush(visitEntity))
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit,
    )
  }
}
