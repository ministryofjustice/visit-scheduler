package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitsBySessionTemplateFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_BOOKED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val telemetryClientService: TelemetryClientService,
  private val eventAuditRepository: EventAuditRepository,
  private val snsService: SnsService,
  @Value("\${visit.cancel.day-limit:28}") private val visitCancellationDayLimit: Int,
) {

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  @Autowired
  private lateinit var applicationService: ApplicationService

  @Autowired
  private lateinit var sessionTemplateService: SessionTemplateService

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
    const val AMEND_EXPIRED_ERROR_MESSAGE = "Visit with booking reference - %s is in the past, it cannot be %s"
  }

  fun bookVisit(applicationReference: String, bookingRequestDto: BookingRequestDto): VisitDto {
    if (applicationService.isApplicationCompleted(applicationReference)) {
      LOG.debug("The application $applicationReference has already been booked!")
      // If already booked then just return object and do nothing more!
      val visit = visitRepository.findVisitByApplicationReference(applicationReference)!!
      return visitDtoBuilder.build(visit)
    }

    val hasExistingBooking = visitRepository.doesBookedVisitExist(applicationReference)
    val application = applicationService.getApplicationEntity(applicationReference)
    val booking = createBooking(application, hasExistingBooking)
    application.completed = true

    val bookedVisitDto = visitDtoBuilder.build(booking)

    eventAuditRepository.updateVisitApplication(application.reference, bookingRequestDto.applicationMethodType)

    saveEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      if (hasExistingBooking) UPDATED_VISIT else BOOKED_VISIT,
      bookingRequestDto.applicationMethodType,
    )

    processBookingEvents(booking, bookedVisitDto, bookingRequestDto, hasExistingBooking)

    return bookedVisitDto
  }

  private fun createBooking(application: Application, hasExistingBooking: Boolean): Visit {
    val existingBooking = if (hasExistingBooking) visitRepository.findVisitByApplicationReference(application.reference) else null
    if (hasExistingBooking) {
      validateVisitStartDate(existingBooking!!, "changed")
    }

    val visitRoom = sessionTemplateService.getVisitRoom(application.sessionSlot.sessionTemplateReference!!)

    val booking = existingBooking?.let {
      // Update existing booking
      existingBooking.sessionSlotId = application.sessionSlotId
      existingBooking.sessionSlot = application.sessionSlot
      existingBooking.visitType = application.visitType
      existingBooking.visitRestriction = application.restriction
      existingBooking.visitRoom = visitRoom
      existingBooking.visitStatus = BOOKED
      return existingBooking
    } ?: run {
      // Create new booking
      Visit(
        prisonId = application.prisonId,
        prison = application.prison,
        prisonerId = application.prisonerId,
        sessionSlotId = application.sessionSlotId,
        sessionSlot = application.sessionSlot,
        visitType = application.visitType,
        visitRestriction = application.restriction,
        visitRoom = visitRoom,
        visitStatus = BOOKED,
      )
    }

    if (hasNotBeenAddedToBooking(booking, application)) {
      booking.applications.add(application)
    }

    with(application.visitContact!!) {
      booking.visitContact = VisitContact(visit = booking, visitId = booking.id, name = name, telephone = telephone)
    }

    application.support?.let {
      booking.support.clear()
      application.support.map { applicationSupport ->
        with(applicationSupport) {
          booking.support.add(VisitSupport(visit = booking, visitId = booking.id, type = type, text = text))
        }
      }
    }

    application.visitors!!.let {
      it.clear()
      it.map { applicationVisitor ->
        with(applicationVisitor) {
          booking.visitors.add(VisitVisitor(visit = booking, visitId = booking.id, nomisPersonId = nomisPersonId, visitContact = contact))
        }
      }
    }

    visitRepository.saveAndFlush(booking)

    return booking
  }

  private fun hasNotBeenAddedToBooking(booking: Visit, application: Application): Boolean {
    return booking.applications.any { it.id == application.id }
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return visitDtoBuilder.build(canceledVisit)
    }

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    validateCancelRequest(visitEntity)

    val cancelOutcome = cancelVisitDto.cancelOutcome

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    val visitDto = visitDtoBuilder.build(visitRepository.saveAndFlush(visitEntity))
    processCancelEvents(visitEntity, visitDto, cancelVisitDto)

    saveEventAudit(cancelVisitDto.actionedBy, visitDto, CANCELLED_VISIT, cancelVisitDto.applicationMethodType)

    // delete any visit notifications from the visit notifications table
    visitNotificationEventRepository.deleteByBookingReference(reference)

    return visitDto
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilterPageableDescending(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<VisitDto> {
    if (visitFilter.prisonCode == null && visitFilter.prisonerId == null) {
      throw ValidationException("Must have prisonId or prisonerId")
    }

    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS)
    return visitRepository.findVisitsOrderByDateAndTime(visitFilter, page).map { visitDtoBuilder.build(it) }
  }

  @Transactional(readOnly = true)
  fun findVisitsBySessionTemplateFilterPageableDescending(
    sessionTemplateReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatusList: List<VisitStatus>,
    visitRestrictions: List<VisitRestriction>?,
    pageablePage: Int? = null,
    pageableSize: Int? = null,
  ): Page<VisitDto> {
    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Visit::createTimestamp.name).descending())

    val filter = VisitsBySessionTemplateFilter(
      sessionTemplateReference = sessionTemplateReference,
      fromDate = fromDate,
      toDate = toDate,
      visitStatusList = visitStatusList,
      visitRestrictions = visitRestrictions,
    )
    return visitRepository.findVisitsOrderByCreateTimestamp(filter, page).map { visitDtoBuilder.build(it) }
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

  private fun validateCancelRequest(visitEntity: Visit) {
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

  fun getBookedVisits(
    prisonerNumber: String? = null,
    prisonCode: String? = null,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> {
    val visitFilter = VisitFilter(
      prisonerId = prisonerNumber,
      prisonCode = prisonCode,
      visitStatusList = listOf(BOOKED),
      startDateTime = startDateTime,
      endDateTime = endDateTime,
    )
    return visitRepository.findVisits(visitFilter).map { visitDtoBuilder.build(it) }
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    val visitEntity = visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found")
    return visitDtoBuilder.build(visitEntity)
  }

  @Transactional(readOnly = true)
  fun getHistoryByReference(bookingReference: String): List<EventAuditDto> {
    return eventAuditRepository.findByBookingReferenceOrderById(bookingReference).map { EventAuditDto(it) }
  }

  @Transactional(readOnly = true)
  fun getLastUserNameToUpdateToSlotByReference(bookingReference: String): String {
    return eventAuditRepository.getLastUserToUpdateSlotByReference(bookingReference)
  }

  @Transactional(readOnly = true)
  fun getLastEventForBooking(bookingReference: String): EventAuditDto? {
    return eventAuditRepository.findLastBookedVisitEventByBookingReference(bookingReference)?.let {
      EventAuditDto(it)
    }
  }

  private fun validateVisitStartDate(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    if (visit.sessionSlot.slotDate.atTime(visit.sessionSlot.slotTime).isBefore(allowedVisitStartDate)) {
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

  fun getFutureVisitsBy(prisonerNumber: String, prisonCode: String?, startDateTime: LocalDateTime = LocalDateTime.now(), endDateTime: LocalDateTime ? = null): List<VisitDto> {
    return this.visitRepository.getVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }
  }
}
