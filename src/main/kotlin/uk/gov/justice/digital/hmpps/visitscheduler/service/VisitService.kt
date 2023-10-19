package uk.gov.justice.digital.hmpps.visitscheduler.service

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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.SUPERSEDED_CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_BOOKED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClientService: TelemetryClientService,
  private val sessionTemplateService: SessionTemplateService,
  private val eventAuditRepository: EventAuditRepository,
  private val snsService: SnsService,
  private val prisonConfigService: PrisonConfigService,
  @Value("\${task.expired-visit.validity-minutes:10}") private val expiredPeriodMinutes: Int,
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
    val visitDto = createApplication(bookingReference, reserveVisitSlotDto)

    saveEventAudit(
      reserveVisitSlotDto.actionedBy,
      visitDto,
      if (visitDto.visitStatus == RESERVED) RESERVED_VISIT else CHANGING_VISIT,
      NOT_KNOWN,
    )

    return visitDto
  }

  fun reserveVisitSlot(bookingReference: String = "", reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {
    val visitDto = createApplication(bookingReference, reserveVisitSlotDto)

    saveEventAudit(
      reserveVisitSlotDto.actionedBy,
      visitDto,
      RESERVED_VISIT,
      NOT_KNOWN,
    )

    return visitDto
  }

  private fun createApplication(bookingReference: String = "", reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {
    val sessionTemplate = sessionTemplateService.getSessionTemplates(reserveVisitSlotDto.sessionTemplateReference)

    val prison = prisonConfigService.findPrisonByCode(sessionTemplate.prisonCode)

    val visitEntity = visitRepository.saveAndFlush(
      Visit(
        prisonerId = reserveVisitSlotDto.prisonerId,
        prison = prison,
        prisonId = prison.id,
        visitRoom = sessionTemplate.visitRoom,
        sessionTemplateReference = reserveVisitSlotDto.sessionTemplateReference,
        visitType = sessionTemplate.visitType,
        visitStatus = getStartingStatus(bookingReference, reserveVisitSlotDto, sessionTemplate),
        visitRestriction = reserveVisitSlotDto.visitRestriction,
        visitStart = reserveVisitSlotDto.startTimestamp,
        visitEnd = reserveVisitSlotDto.endTimestamp,
        _reference = bookingReference,
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

    val visitDto = VisitDto(visitEntity)

    val eventName = if (bookingReference.isBlank()) VISIT_SLOT_RESERVED_EVENT else VISIT_CHANGED_EVENT
    telemetryClientService.trackEvent(eventName, telemetryClientService.createVisitTrackEventFromVisitEntity(visitEntity, reserveVisitSlotDto.actionedBy))
    return visitDto
  }

  private fun getStartingStatus(
    bookingReference: String,
    reserveVisitSlotDto: ReserveVisitSlotDto,
    sessionTemplate: SessionTemplateDto,
  ): VisitStatus {
    val bookedVisit = this.visitRepository.findBookedVisit(bookingReference)

    if (bookedVisit == null ||
      bookedVisit.prison.code != sessionTemplate.prisonCode ||
      bookedVisit.prisonerId != reserveVisitSlotDto.prisonerId ||
      bookedVisit.visitRestriction != reserveVisitSlotDto.visitRestriction ||
      bookedVisit.visitStart.compareTo(reserveVisitSlotDto.startTimestamp) != 0 ||
      !bookedVisit.sessionTemplateReference.equals(reserveVisitSlotDto.sessionTemplateReference)
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
    val visitEntity = getApplication(applicationReference)

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

    visitEntity.sessionTemplateReference = changeVisitSlotRequestDto.sessionTemplateReference

    telemetryClientService.trackEvent(
      VISIT_SLOT_CHANGED_EVENT,
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
    applicationReferences.forEach {
      val applicationToBeDeleted = getApplication(it)
      val deleted = visitRepository.deleteByApplicationReferenceAndVisitStatusIn(it, EXPIRED_VISIT_STATUSES)
      if (deleted > 0) {
        val bookEvent = telemetryClientService.createVisitTrackEventFromVisitEntity(applicationToBeDeleted)
        telemetryClientService.trackEvent(VISIT_SLOT_RELEASED_EVENT, bookEvent)
      }
    }
  }

  fun bookVisit(applicationReference: String, bookingRequestDto: BookingRequestDto): VisitDto {
    if (visitRepository.isApplicationBooked(applicationReference)) {
      LOG.debug("The application $applicationReference has already been booked!")
      // If already booked then just return object and do nothing more!
      val bookedApplication = visitRepository.findBookedApplication(applicationReference)!!
      return VisitDto(bookedApplication)
    }

    val bookingReferenceFromApplication = visitRepository.getApplicationBookingReference(applicationReference)
      ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
    val hasExistingBooking = visitRepository.doesBookedVisitExist(bookingReferenceFromApplication)
    if (hasExistingBooking) {
      // check if the existing visit is in the past
      validateVisitStartDate(visitRepository.findBookedVisit(bookingReferenceFromApplication)!!, "changed")
    }

    val visitToBook = getApplication(applicationReference)

    visitRepository.findBookedVisit(visitToBook.reference)?.let { existingBooking ->
      existingBooking.visitStatus = CANCELLED
      existingBooking.outcomeStatus = SUPERSEDED_CANCELLATION
      visitRepository.saveAndFlush(existingBooking)
    }

    visitToBook.visitStatus = BOOKED

    val bookedVisitDto = VisitDto(visitRepository.saveAndFlush(visitToBook))

    eventAuditRepository.updateVisitApplication(bookedVisitDto.applicationReference, bookingRequestDto.applicationMethodType)

    saveEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      if (hasExistingBooking) UPDATED_VISIT else BOOKED_VISIT,
      bookingRequestDto.applicationMethodType,
    )

    processBookingEvents(visitToBook, bookedVisitDto, bookingRequestDto, hasExistingBooking)

    return bookedVisitDto
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return VisitDto(canceledVisit)
    }

    validateCancelRequest(reference)

    val cancelOutcome = cancelVisitDto.cancelOutcome
    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    val visitDto = VisitDto(visitRepository.saveAndFlush(visitEntity))
    processCancelEvents(visitEntity, visitDto, cancelVisitDto)

    saveEventAudit(
      cancelVisitDto.actionedBy,
      visitDto,
      CANCELLED_VISIT,
      cancelVisitDto.applicationMethodType,
    )

    return visitDto
  }

  private fun getApplication(applicationReference: String): Visit {
    val visit = visitRepository.findApplication(applicationReference)
      ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
    return visit
  }

  private fun saveEventAudit(
    actionedBy: String,
    visit: VisitDto,
    type: EventAuditType,
    applicationMethodType: ApplicationMethodType,
  ) {
    eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visit.reference,
        applicationReference = visit.applicationReference,
        sessionTemplateReference = visit.sessionTemplateReference,
        type = type,
        applicationMethodType = applicationMethodType,
      ),
    )
  }

  private fun createVisitNote(visit: Visit, type: VisitNoteType, text: String): VisitNote {
    return VisitNote(
      visitId = visit.id,
      type = type,
      text = text,
      visit = visit,
    )
  }

  private fun processBookingEvents(
    bookedVisit: Visit,
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
    hasExistingBooking: Boolean,
  ) {
    val bookEvent = telemetryClientService.createVisitTrackEventFromVisitEntity(bookedVisit, bookingRequestDto.actionedBy, bookingRequestDto.applicationMethodType)
    bookEvent["isUpdated"] = hasExistingBooking.toString()
    telemetryClientService.trackEvent(VISIT_BOOKED_EVENT, bookEvent)

    if (hasExistingBooking) {
      snsService.sendChangedVisitBookedEvent(bookedVisitDto)
    } else {
      snsService.sendVisitBookedEvent(bookedVisitDto)
    }
  }

  private fun validateCancelRequest(reference: String) {
    val visitEntity =
      visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    validateVisitStartDate(
      visitEntity,
      "cancelled",
      getAllowedCancellationDate(visitCancellationDayLimit = visitCancellationDayLimit),
    )
  }

  private fun processCancelEvents(
    visit: Visit,
    visitDto: VisitDto,
    cancelVisitDto: CancelVisitDto,
  ) {
    val eventsMap = telemetryClientService.createVisitTrackEventFromVisitEntity(visit, cancelVisitDto.actionedBy, cancelVisitDto.applicationMethodType)
    visitDto.outcomeStatus?.let {
      eventsMap.put("outcomeStatus", it.name)
    }
    telemetryClientService.trackEvent(
      VISIT_CANCELLED_EVENT,
      eventsMap,
    )
    snsService.sendVisitCancelledEvent(visitDto)
  }

  fun getBookedVisits(prisonerNumber: String, prisonCode: String?, startDateTime: LocalDateTime, endDateTime: LocalDateTime? = null): List<VisitDto> {
    val visitFilter = VisitFilter(
      prisonerId = prisonerNumber,
      prisonCode = prisonCode,
      startDateTime = startDateTime,
      endDateTime = endDateTime,
      visitStatusList = listOf(BOOKED),
    )
    return visitRepository.findAll(VisitSpecification(visitFilter)).map { VisitDto(it) }
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found"))
  }

  @Transactional(readOnly = true)
  fun getHistoryByReference(bookingReference: String): List<EventAuditDto> {
    return eventAuditRepository.findByBookingReferenceOrderById(bookingReference).map { EventAuditDto(it) }
  }

  @Transactional(readOnly = true)
  fun getLastEventForBooking(bookingReference: String): EventAuditDto {
    return EventAuditDto(eventAuditRepository.findLastBookedVisitEventByBookingReference(bookingReference))
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

  fun getFutureVisitsBy(prisonerNumber: String, prisonCode: String): List<VisitDto> {
    return this.visitRepository.getVisits(prisonerNumber, prisonCode, LocalDateTime.now()).map { VisitDto(it) }
  }
}
