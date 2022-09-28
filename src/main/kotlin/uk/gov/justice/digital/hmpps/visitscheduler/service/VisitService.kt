package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.SUPERSEDED_CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Supplier

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClient: TelemetryClient,
  private val snsService: SnsService,
  @Value("\${task.expired-visit.validity-minutes:20}") private val expiredPeriodMinutes: Int
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
    val EXPIRED_VISIT_STATUSES = listOf<VisitStatus>(RESERVED, CHANGING)
  }

  fun changeBookedVisit(bookingReference: String, reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {

    if (!visitRepository.isValidBookingReference(bookingReference)) {
      throw VisitNotFoundException("Visit booking reference $bookingReference not found")
    }

    return reserveVisitSlot(bookingReference, reserveVisitSlotDto)
  }

  fun reserveVisitSlot(bookingReference: String = "", reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = reserveVisitSlotDto.prisonerId,
        prisonId = reserveVisitSlotDto.prisonId,
        visitRoom = reserveVisitSlotDto.visitRoom,
        visitType = reserveVisitSlotDto.visitType,
        visitStatus = getStartingStatus(bookingReference, reserveVisitSlotDto),
        visitRestriction = reserveVisitSlotDto.visitRestriction,
        visitStart = reserveVisitSlotDto.startTimestamp,
        visitEnd = reserveVisitSlotDto.endTimestamp,
        _reference = bookingReference
      )
    )

    reserveVisitSlotDto.visitContact?.let {
      visitEntity.visitContact = createVisitContact(visitEntity, it.name, it.telephone)
    }

    reserveVisitSlotDto.visitors.forEach {
      visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.visitContact))
    }

    reserveVisitSlotDto.visitorSupport?.let { supportList ->
      supportList.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        visitEntity.support.add(createVisitSupport(visitEntity, it.type, it.text))
      }
    }

    createVisitTrackEvent(visitEntity)
    return VisitDto(visitEntity)
  }

  private fun getStartingStatus(bookingReference: String, reserveVisitSlotDto: ReserveVisitSlotDto): VisitStatus {

    val bookedVisit = this.visitRepository.findBookedVisit(bookingReference)

    if (bookedVisit == null ||
      (bookedVisit.prisonId != reserveVisitSlotDto.prisonId) ||
      (bookedVisit.prisonerId != reserveVisitSlotDto.prisonerId) ||
      (bookedVisit.visitRestriction != reserveVisitSlotDto.visitRestriction) ||
      (bookedVisit.visitStart.compareTo(reserveVisitSlotDto.startTimestamp) != 0)
    ) {
      return RESERVED
    }
    return CHANGING
  }

  private fun getUpdatedStatus(visitEntity: Visit, changeVisitSlotRequestDto: ChangeVisitSlotRequestDto): VisitStatus {

    val bookedVisit = this.visitRepository.findBookedVisit(visitEntity.reference)

    if (bookedVisit == null ||
      (changeVisitSlotRequestDto.visitRestriction != null && bookedVisit.visitRestriction != changeVisitSlotRequestDto.visitRestriction) ||
      (changeVisitSlotRequestDto.startTimestamp != null && bookedVisit.visitStart.compareTo(changeVisitSlotRequestDto.startTimestamp) != 0)
    ) {
      return RESERVED
    }
    return visitEntity.visitStatus
  }

  fun changeVisitSlot(applicationReference: String, changeVisitSlotRequestDto: ChangeVisitSlotRequestDto): VisitDto {
    val visitEntity = visitRepository.findByApplicationReference(applicationReference) ?: throw VisitNotFoundException("Reserved visit reference $applicationReference not found")

    changeVisitSlotRequestDto.visitRestriction?.let { visitRestriction -> visitEntity.visitRestriction = visitRestriction }
    changeVisitSlotRequestDto.startTimestamp?.let {
      visitEntity.visitStart = it
    }
    visitEntity.visitStatus = getUpdatedStatus(visitEntity, changeVisitSlotRequestDto)

    changeVisitSlotRequestDto.endTimestamp?.let { visitEnd -> visitEntity.visitEnd = visitEnd }

    changeVisitSlotRequestDto.visitContact?.let { visitContactUpdate ->
      visitEntity.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        visitEntity.visitContact = createVisitContact(visitEntity, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    changeVisitSlotRequestDto.visitors?.let { visitorsUpdate ->
      visitEntity.visitors.clear()
      visitRepository.saveAndFlush(visitEntity)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.visitContact))
      }
    }

    changeVisitSlotRequestDto.visitorSupport?.let { visitSupportUpdate ->
      visitEntity.support.clear()
      visitRepository.saveAndFlush(visitEntity)
      visitSupportUpdate.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        visitEntity.support.add(createVisitSupport(visitEntity, it.type, it.text))
      }
    }

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-updated",
      listOfNotNull(
        "reference" to visitEntity.reference,
        "prisonerId" to visitEntity.prisonerId,
        "prisonId" to visitEntity.prisonId,
        "visitType" to visitEntity.visitType.name,
        "visitRoom" to visitEntity.visitRoom,
        if (changeVisitSlotRequestDto.visitRestriction != null) "visitRestriction" to changeVisitSlotRequestDto.visitRestriction.name else null,
        if (changeVisitSlotRequestDto.startTimestamp != null) "visitStart" to changeVisitSlotRequestDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME) else null,
        "visitStatus" to visitEntity.visitStatus.name
      ).toMap(),
      null
    )

    return VisitDto(visitEntity)
  }

  @Suppress("KotlinDeprecation")
  @Deprecated("this should not be used ")
  fun createVisit(createVisitRequest: CreateVisitRequestDto): VisitDto {
    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = createVisitRequest.prisonerId,
        prisonId = createVisitRequest.prisonId,
        visitRoom = createVisitRequest.visitRoom,
        visitType = createVisitRequest.visitType,
        visitStatus = RESERVED,
        visitRestriction = createVisitRequest.visitRestriction,
        visitStart = createVisitRequest.startTimestamp,
        visitEnd = createVisitRequest.endTimestamp
      )
    )

    createVisitRequest.visitContact?.let {
      visitEntity.visitContact = createVisitContact(visitEntity, it.name, it.telephone)
    }

    createVisitRequest.visitors.forEach {
      visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.visitContact))
    }

    createVisitRequest.visitorSupport?.let { supportList ->
      supportList.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        visitEntity.support.add(createVisitSupport(visitEntity, it.type, it.text))
      }
    }

    createVisitTrackEvent(visitEntity)

    return VisitDto(visitEntity)
  }

  private fun createVisitTrackEvent(visitEntity: Visit) {
    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-created",
      mapOf(
        "reference" to visitEntity.reference,
        "prisonerId" to visitEntity.prisonerId,
        "prisonId" to visitEntity.prisonId,
        "visitType" to visitEntity.visitType.name,
        "visitRoom" to visitEntity.visitRoom,
        "visitRestriction" to visitEntity.visitRestriction.name,
        "visitStart" to visitEntity.visitStart.format(DateTimeFormatter.ISO_DATE_TIME),
        "visitStatus" to visitEntity.visitStatus.name
      ),
      null
    )
  }

  @Deprecated("See find visits pageable", ReplaceWith("findVisitsByFilterPageableDescending(visitFilter).content"))
  fun findVisitsByFilter(visitFilter: VisitFilter): List<VisitDto> {
    return findVisitsByFilterPageableDescending(visitFilter).content
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilterPageableDescending(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<VisitDto> {
    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Visit::visitStart.name).descending())
    return visitRepository.findAll(VisitSpecification(visitFilter), page).map { VisitDto(it) }
  }

  @Transactional(readOnly = true)
  fun findExpiredApplicationReferences(): List<String> {
    val localDateTime = getReservedExpiredDateAndTime()
    log.debug("Entered findExpiredApplicationReferences :" + localDateTime)
    return visitRepository.findExpiredApplicationReferences(localDateTime)
  }

  fun getReservedExpiredDateAndTime(): LocalDateTime {
    return LocalDateTime.now().minusMinutes(expiredPeriodMinutes.toLong())
  }

  @Suppress("KotlinDeprecation")
  @Deprecated("this should not be used ")
  fun updateVisit(reference: String, updateVisitRequest: UpdateVisitRequestDto): VisitDto {
    val visitEntity = visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found")

    updateVisitRequest.prisonerId?.let { prisonerId -> visitEntity.prisonerId = prisonerId }
    updateVisitRequest.prisonId?.let { prisonId -> visitEntity.prisonId = prisonId }
    updateVisitRequest.visitRoom?.let { visitRoom -> visitEntity.visitRoom = visitRoom }
    updateVisitRequest.visitType?.let { visitType -> visitEntity.visitType = visitType }
    updateVisitRequest.visitStatus?.let { status -> visitEntity.visitStatus = status }
    updateVisitRequest.visitRestriction?.let { visitRestriction -> visitEntity.visitRestriction = visitRestriction }
    updateVisitRequest.startTimestamp?.let { visitStart -> visitEntity.visitStart = visitStart }
    updateVisitRequest.endTimestamp?.let { visitEnd -> visitEntity.visitEnd = visitEnd }

    updateVisitRequest.visitContact?.let { visitContactUpdate ->
      visitEntity.visitContact?.let { visitContact ->
        visitContact.name = visitContactUpdate.name
        visitContact.telephone = visitContactUpdate.telephone
      } ?: run {
        visitEntity.visitContact = createVisitContact(visitEntity, visitContactUpdate.name, visitContactUpdate.telephone)
      }
    }

    updateVisitRequest.visitors?.let { visitorsUpdate ->
      visitEntity.visitors.clear()
      visitRepository.saveAndFlush(visitEntity)
      visitorsUpdate.distinctBy { it.nomisPersonId }.forEach {
        visitEntity.visitors.add(createVisitVisitor(visitEntity, it.nomisPersonId, it.visitContact))
      }
    }

    updateVisitRequest.visitorSupport?.let { visitSupportUpdate ->
      visitEntity.support.clear()
      visitRepository.saveAndFlush(visitEntity)
      visitSupportUpdate.forEach {
        if (!supportTypeRepository.existsByName(it.type)) {
          throw SupportNotFoundException("Invalid support ${it.type} not found")
        }
        visitEntity.support.add(createVisitSupport(visitEntity, it.type, it.text))
      }
    }

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-updated",
      listOfNotNull(
        "reference" to reference,
        if (updateVisitRequest.prisonerId != null) "prisonerId" to updateVisitRequest.prisonerId else null,
        if (updateVisitRequest.prisonId != null) "prisonId" to updateVisitRequest.prisonId else null,
        if (updateVisitRequest.visitType != null) "visitType" to updateVisitRequest.visitType.name else null,
        if (updateVisitRequest.visitRoom != null) "visitRoom" to updateVisitRequest.visitRoom else null,
        if (updateVisitRequest.visitRestriction != null) "visitRestriction" to updateVisitRequest.visitRestriction.name else null,
        if (updateVisitRequest.startTimestamp != null) "visitStart" to updateVisitRequest.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME) else null,
        if (updateVisitRequest.visitStatus != null) "visitStatus" to updateVisitRequest.visitStatus.name else null
      ).toMap(),
      null
    )

    return VisitDto(visitEntity)
  }

  fun deleteAllExpiredVisitsByApplicationReference(applicationReferences: List<String>) {

    visitRepository.deleteAllByApplicationReferenceInAndVisitStatusIn(applicationReferences, EXPIRED_VISIT_STATUSES)

    for (applicationReference in applicationReferences) {
      telemetryClient.trackEvent(
        "visit-scheduler-prison-visit-deleted",
        mapOf(
          "applicationReferences" to applicationReference
        ),
        null
      )
    }
  }

  fun bookVisit(applicationReference: String): VisitDto {

    val visitToBook = visitRepository.findByApplicationReference(applicationReference) ?: throw VisitNotFoundException("Could not find reserved visit applicationReference:$applicationReference not found")
    val existingBookedVisit = visitRepository.findBookedVisit(visitToBook.reference)
    val changedVisit = existingBookedVisit != null
    if (changedVisit) {
      existingBookedVisit?.let {
        it.visitStatus = VisitStatus.CANCELLED
        it.outcomeStatus = SUPERSEDED_CANCELLATION
        visitRepository.saveAndFlush(it)
      }
    }

    visitToBook.visitStatus = VisitStatus.BOOKED

    val visit = VisitDto(visitRepository.saveAndFlush(visitToBook))

    // TODO can we change the name of the track event name?
    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-updated",
      listOfNotNull(
        "reference" to visitToBook.reference,
        "visitStatus" to visit.visitStatus.name,
      ).toMap(),
      null
    )

    if (changedVisit) {
      snsService.sendChangedVisitBookedEvent(visit)
    } else {
      snsService.sendVisitBookedEvent(visit)
    }

    return visit
  }

  fun cancelVisit(reference: String, cancelOutcome: OutcomeDto): VisitDto {
    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")

    visitEntity.visitStatus = VisitStatus.CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    visitRepository.saveAndFlush(visitEntity)

    telemetryClient.trackEvent(
      "visit-scheduler-prison-visit-cancelled",
      mapOf(
        "reference" to reference,
        "visitStatus" to visitEntity.visitStatus.name,
        "outcomeStatus" to visitEntity.outcomeStatus?.name
      ),
      null
    )

    val visit = VisitDto(visitEntity)
    snsService.sendVisitCancelledEvent(visit)
    return visit
  }

  @Transactional(readOnly = true)
  fun getBookedVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit reference $reference not found"))
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit
    )
  }

  private fun createVisitContact(visit: Visit, name: String, telephone: String): VisitContact {
    return VisitContact(
      visitId = visit.id,
      name = name,
      telephone = telephone,
      visit = visit
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long, visitContact: Boolean?): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      visit = visit,
      visitContact = visitContact
    )
  }

  private fun createVisitSupport(visit: Visit, type: String, text: String?): VisitSupport {
    return VisitSupport(
      type = type,
      visitId = visit.id,
      text = text,
      visit = visit
    )
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
