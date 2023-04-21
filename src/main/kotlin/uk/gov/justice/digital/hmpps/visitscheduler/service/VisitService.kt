package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.SUPERSEDED_CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
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
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClient: TelemetryClient,
  private val snsService: SnsService,
  private val authenticationHelperService: AuthenticationHelperService,
  private val prisonConfigService: PrisonConfigService,
  @Value("\${task.expired-visit.validity-minutes:20}") private val expiredPeriodMinutes: Int,
  @Value("\${visit.cancel.day-limit:28}") private val visitCancellationDayLimit: Int,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
    val EXPIRED_VISIT_STATUSES = listOf(RESERVED, CHANGING)
    const val AMEND_EXPIRED_ERROR_MESSAGE = "Visit with booking reference - %s is in the past, it cannot be %s"
  }

  fun changeBookedVisit(bookingReference: String, reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {
    val visit = visitRepository.findBookedVisit(bookingReference)
      ?: throw VisitNotFoundException("Visit booking reference $bookingReference not found")

    // check if the existing visit is in the past
    validateVisitStartDate(visit, "changed")
    return reserveVisitSlot(bookingReference, reserveVisitSlotDto)
  }

  fun reserveVisitSlot(bookingReference: String = "", reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {
    val prison = prisonConfigService.findPrisonByCode(reserveVisitSlotDto.prisonCode)

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = reserveVisitSlotDto.prisonerId,
        prison = prison,
        prisonId = prison.id,
        capacityGroup = reserveVisitSlotDto.capacityGroup,
        visitType = reserveVisitSlotDto.visitType,
        visitStatus = getStartingStatus(bookingReference, reserveVisitSlotDto),
        visitRestriction = reserveVisitSlotDto.visitRestriction,
        visitStart = reserveVisitSlotDto.startTimestamp,
        visitEnd = reserveVisitSlotDto.endTimestamp,
        _reference = bookingReference,
        createdBy = reserveVisitSlotDto.actionedBy,
      ),
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

    val eventName = if (bookingReference.isBlank()) TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT.eventName else TelemetryVisitEvents.VISIT_CHANGED_EVENT.eventName
    trackEvent(eventName, createVisitTrackEventFromVisitEntity(visitEntity, reserveVisitSlotDto.actionedBy))

    return VisitDto(visitEntity)
  }

  private fun getStartingStatus(bookingReference: String, reserveVisitSlotDto: ReserveVisitSlotDto): VisitStatus {
    val bookedVisit = this.visitRepository.findBookedVisit(bookingReference)

    if (bookedVisit == null ||
      (bookedVisit.prison.code != reserveVisitSlotDto.prisonCode) ||
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
    return CHANGING
  }

  fun changeVisitSlot(applicationReference: String, changeVisitSlotRequestDto: ChangeVisitSlotRequestDto): VisitDto {
    val visitEntity = visitRepository.findApplication(applicationReference) ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")

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

    trackEvent(
      TelemetryVisitEvents.VISIT_SLOT_CHANGED_EVENT.eventName,
      mapOf(
        "applicationReference" to visitEntity.applicationReference,
        "reference" to visitEntity.reference,
        "visitStatus" to visitEntity.visitStatus.name,
      ),
    )

    return VisitDto(visitEntity)
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilterPageableDescending(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<VisitDto> {
    if (visitFilter.prisonCode == null && visitFilter.prisonerId == null) {
      throw ValidationException("Must have prisonId or prisonerId")
    }

    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Visit::visitStart.name).descending())
    return visitRepository.findAll(VisitSpecification(visitFilter), page).map { VisitDto(it) }
  }

  @Transactional(readOnly = true)
  fun findExpiredApplicationReferences(): List<String> {
    val localDateTime = getReservedExpiredDateAndTime()
    LOG.debug("Entered findExpiredApplicationReferences : $localDateTime")
    return visitRepository.findExpiredApplicationReferences(localDateTime)
  }

  fun getReservedExpiredDateAndTime(): LocalDateTime {
    return LocalDateTime.now().minusMinutes(expiredPeriodMinutes.toLong())
  }

  fun deleteAllExpiredVisitsByApplicationReference(applicationReferences: List<String>) {
    visitRepository.deleteAllByApplicationReferenceInAndVisitStatusIn(applicationReferences, EXPIRED_VISIT_STATUSES)

    telemetryClient.trackEvent(
      TelemetryVisitEvents.VISIT_DELETED_EVENT.eventName,
      mapOf(
        "applicationReferences" to applicationReferences.joinToString(","),
      ),
      null,
    )
  }

  fun bookVisit(applicationReference: String): VisitDto {
    if (visitRepository.isApplicationBooked(applicationReference)) {
      LOG.debug("The application $applicationReference has already been booked!")
      // If already booked then just return object and do nothing more!
      val bookedApplication = visitRepository.findBookedApplication(applicationReference)!!
      return VisitDto(bookedApplication)
    }

    val visitToBook = visitRepository.findApplication(applicationReference) ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
    val existingBookedVisit = visitRepository.findBookedVisit(visitToBook.reference)

    // check if the existing visit is in the past
    existingBookedVisit?.let {
      validateVisitStartDate(existingBookedVisit, "changed")
    }

    val changedVisit = existingBookedVisit != null
    if (changedVisit) {
      // the existing booking should always be saved before the new booking, see VisitRepository.findByReference

      existingBookedVisit?.let { existingBooking ->
        existingBooking.visitStatus = CANCELLED
        existingBooking.outcomeStatus = SUPERSEDED_CANCELLATION
        existingBooking.cancelledBy = visitToBook.createdBy
        visitRepository.saveAndFlush(existingBooking)

        // set the new bookings updated by to current username and set createdBy to existing booking username
        visitToBook.updatedBy = visitToBook.createdBy
        visitToBook.createdBy = existingBooking.createdBy
      }
    }

    visitToBook.visitStatus = VisitStatus.BOOKED

    val visit = VisitDto(visitRepository.saveAndFlush(visitToBook))

    val bookEvent = createVisitTrackEventFromVisitEntity(visitToBook, visitToBook.createdBy)
    bookEvent["isUpdated"] = changedVisit.toString()

    trackEvent(TelemetryVisitEvents.VISIT_BOOKED_EVENT.eventName, bookEvent)

    if (changedVisit) {
      snsService.sendChangedVisitBookedEvent(visit)
    } else {
      snsService.sendVisitBookedEvent(visit)
    }

    return visit
  }

  @Deprecated("This method has been deprecated.")
  fun cancelVisit(reference: String, outcomeDto: OutcomeDto): VisitDto {
    val cancelVisitDto = CancelVisitDto(outcomeDto, authenticationHelperService.currentUserName)
    return cancelVisit(reference, cancelVisitDto)
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val cancelOutcome = cancelVisitDto.cancelOutcome

    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return VisitDto(canceledVisit)
    }

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    validateVisitStartDate(visitEntity, "cancelled", getAllowedCancellationDate(visitCancellationDayLimit = visitCancellationDayLimit))

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus
    visitEntity.cancelledBy = cancelVisitDto.actionedBy

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    visitRepository.saveAndFlush(visitEntity)

    val eventsMap = createVisitTrackEventFromVisitEntity(visitEntity, cancelVisitDto.actionedBy)
    visitEntity.outcomeStatus?.let {
      eventsMap.put("outcomeStatus", it.name)
    }

    trackEvent(
      TelemetryVisitEvents.VISIT_CANCELLED_EVENT.eventName,
      eventsMap,
    )

    val visit = VisitDto(visitEntity)
    snsService.sendVisitCancelledEvent(visit)

    return visit
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found"))
  }

  @Transactional(readOnly = true)
  fun getVisitHistoryByReference(reference: String): List<VisitDto> {
    return visitRepository.findAllByReference(reference).map { VisitDto(it) }
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit,
    )
  }

  private fun createVisitContact(visit: Visit, name: String, telephone: String): VisitContact {
    return VisitContact(
      visitId = visit.id,
      name = name,
      telephone = telephone,
      visit = visit,
    )
  }

  private fun createVisitVisitor(visit: Visit, personId: Long, visitContact: Boolean?): VisitVisitor {
    return VisitVisitor(
      nomisPersonId = personId,
      visitId = visit.id,
      visit = visit,
      visitContact = visitContact,
    )
  }

  private fun createVisitSupport(visit: Visit, type: String, text: String?): VisitSupport {
    return VisitSupport(
      type = type,
      visitId = visit.id,
      text = text,
      visit = visit,
    )
  }

  private fun validateVisitStartDate(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    if (visit.visitStart.isBefore(allowedVisitStartDate)) {
      throw ExpiredVisitAmendException(
        AMEND_EXPIRED_ERROR_MESSAGE.format(visit.reference, action),
        ExpiredVisitAmendException("trying to change / cancel an expired visit"),
      )
    }
  }

  private fun getAllowedCancellationDate(currentDateTime: LocalDateTime = LocalDateTime.now(), visitCancellationDayLimit: Int): LocalDateTime {
    var visitCancellationDateAllowed = currentDateTime
    // check if the visit being cancelled is in the past
    if (visitCancellationDayLimit > 0) {
      visitCancellationDateAllowed = visitCancellationDateAllowed.minusDays(visitCancellationDayLimit.toLong()).truncatedTo(ChronoUnit.DAYS)
    }

    return visitCancellationDateAllowed
  }

  private fun createVisitTrackEventFromVisitEntity(visitEntity: Visit, actionedBy: String): MutableMap<String, String> {
    return mutableMapOf(
      "reference" to visitEntity.reference,
      "prisonerId" to visitEntity.prisonerId,
      "prisonId" to visitEntity.prison.code,
      "visitType" to visitEntity.visitType.name,
      "capacityGroup" to (visitEntity.capacityGroup ?: ""),
      "visitRestriction" to visitEntity.visitRestriction.name,
      "visitStart" to visitEntity.visitStart.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visitEntity.visitStatus.name,
      "applicationReference" to visitEntity.applicationReference,
      "actionedBy" to actionedBy,
    )
  }

  private fun trackEvent(eventName: String, properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(eventName, properties, null)
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }
}
