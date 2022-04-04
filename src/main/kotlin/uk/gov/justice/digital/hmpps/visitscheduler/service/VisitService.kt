package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.UpdateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.data.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.specification.VisitSpecification
import java.util.function.Supplier

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
) {

  fun createVisit(createVisitRequest: CreateVisitRequest): VisitDto {
    log.info("Creating visit for prisoner")
    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonId = createVisitRequest.prisonId,
        prisonerId = createVisitRequest.prisonerId,
        visitType = createVisitRequest.visitType,
        status = createVisitRequest.visitStatus,
        visitRestriction = createVisitRequest.visitRestriction,
        visitRoom = createVisitRequest.visitRoom,
        visitStart = createVisitRequest.startTimestamp,
        visitEnd = createVisitRequest.endTimestamp,
        sessionTemplateId = createVisitRequest.sessionId,
      )
    )

    createVisitRequest.visitNotes?.let { visitNotes ->
      visitNotes.forEach {
        visitEntity.visitNotes.add(createVisitNote(visitEntity, it.type, it.text))
      }
    }

    createVisitRequest.mainContact?.let {
      visitEntity.mainContact = createVisitContact(visitEntity, it.contactName, it.contactPhone)
    }

    createVisitRequest.contactList?.let { contactList ->
      contactList.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.leadVisitor))
      }
    }

    createVisitRequest.supportList?.let { supportList ->
      supportList.distinctBy { it.supportName }.forEach {
        supportTypeRepository.findByName(it.supportName) ?: throw SupportNotFoundException("Invalid support ${it.supportName} not found")
        visitEntity.support.add(createVisitSupport(visitEntity, it.supportName, it.supportDetails))
      }
    }

    return VisitDto(visitEntity)
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilter(visitFilter: VisitFilter): List<VisitDto> {
    return visitRepository.findAll(VisitSpecification(visitFilter)).sortedBy { it.visitStart }.map { VisitDto(it) }
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found"))
  }

  fun updateVisit(reference: String, updateVisitRequest: UpdateVisitRequest): VisitDto {
    log.info("Updating visit for $reference")

    val visitEntity = visitRepository.findByReference(reference)
    visitEntity ?: throw VisitNotFoundException("Visit reference $reference not found")

    updateVisitRequest.prisonerId?.let { prisonerId -> visitEntity.prisonerId = prisonerId }
    updateVisitRequest.prisonId?.let { prisonId -> visitEntity.prisonId = prisonId }
    updateVisitRequest.startTimestamp?.let { visitStart -> visitEntity.visitStart = visitStart }
    updateVisitRequest.endTimestamp?.let { visitEnd -> visitEntity.visitEnd = visitEnd }
    updateVisitRequest.visitType?.let { visitType -> visitEntity.visitType = visitType }
    updateVisitRequest.visitStatus?.let { status -> visitEntity.status = status }
    updateVisitRequest.visitRestriction?.let { visitRestriction -> visitEntity.visitRestriction = visitRestriction }
    updateVisitRequest.visitRoom?.let { visitRoom -> visitEntity.visitRoom = visitRoom }
    updateVisitRequest.sessionId?.let { sessionId -> visitEntity.sessionTemplateId = sessionId }

    updateVisitRequest.mainContact?.let { updateContact ->
      visitEntity.mainContact?.let { mainContact ->
        mainContact.contactName = updateContact.contactName
        mainContact.contactPhone = updateContact.contactPhone
      } ?: run {
        visitEntity.mainContact = createVisitContact(visitEntity, updateContact.contactName, updateContact.contactPhone)
      }
    }

    updateVisitRequest.contactList?.let { contactList ->
      visitEntity.visitors.clear()
      visitRepository.saveAndFlush(visitEntity)
      contactList.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.leadVisitor))
      }
    }

    updateVisitRequest.supportList?.let { supportList ->
      visitEntity.support.clear()
      visitRepository.saveAndFlush(visitEntity)
      supportList.distinctBy { it.supportName }.forEach {
        supportTypeRepository.findByName(it.supportName) ?: throw SupportNotFoundException("Invalid support ${it.supportName} not found")
        visitEntity.support.add(createVisitSupport(visitEntity, it.supportName, it.supportDetails))
      }
    }

    visitRepository.saveAndFlush(visitEntity)

    return VisitDto(visitEntity)
  }

  fun deleteVisit(reference: String) {
    val visit = visitRepository.findByReference(reference)
    visit?.let { visitRepository.delete(it) }.also { log.info("Visit with reference $reference deleted") }
      ?: run {
        log.info("Visit reference $reference not found")
      }
  }

  fun deleteAllVisits(visits: List<VisitDto>) {
    visitRepository.deleteAllByReferenceIn(visits.map { it.reference }.toList())
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit
    )
  }

  private fun createVisitContact(visit: Visit, contactName: String, contactPhone: String): VisitContact {
    return VisitContact(
      visitId = visit.id,
      contactName = contactName,
      contactPhone = contactPhone,
      visit = visit
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long, leadVisitor: Boolean): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      leadVisitor = leadVisitor,
      visit = visit
    )
  }

  private fun createVisitSupport(visit: Visit, supportName: String, supportDetails: String?): VisitSupport {
    return VisitSupport(
      supportName = supportName,
      visitId = visit.id,
      supportDetails = supportDetails,
      visit = visit
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class VisitNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<VisitNotFoundException> {
  override fun get(): VisitNotFoundException {
    return VisitNotFoundException(message, cause)
  }
}

class SupportNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<SupportNotFoundException> {
  override fun get(): SupportNotFoundException {
    return SupportNotFoundException(message, cause)
  }
}
