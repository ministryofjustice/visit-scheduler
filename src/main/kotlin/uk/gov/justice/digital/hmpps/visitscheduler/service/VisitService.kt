package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.lang.reflect.InvocationTargetException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val telemetryClientService: TelemetryClientService,
  private val eventAuditRepository: EventAuditRepository,
  private val snsService: SnsService,
  private val applicationValidationService: ApplicationValidationService,
  @Value("\${visit.cancel.day-limit:28}") private val visitCancellationDayLimit: Int,
) {

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  @Lazy
  @Autowired
  private lateinit var visitNotificationEventService: VisitNotificationEventService

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
    // Need to set application complete at earliest opportunity to prevent two bookings from being created, Edge case.
    applicationService.completeApplication(applicationReference)

    val application = applicationService.getApplicationEntity(applicationReference)

    val existingBooking = visitRepository.findVisitByApplicationReference(application.reference)

    // application validity checks
    applicationValidationService.validateApplication(bookingRequestDto, application, existingBooking)

    val booking = createBooking(application, existingBooking)

    return existingBooking?.let {
      processUpdateBookingEvents(booking, bookingRequestDto)
    } ?: run {
      processBookingEvents(booking, bookingRequestDto)
    }
  }

  fun getBookCountForSlot(sessionSlotId: Long, restriction: VisitRestriction): Long {
    return when (restriction) {
      OPEN -> visitRepository.getCountOfBookedForOpenSessionSlot(sessionSlotId)
      CLOSED -> visitRepository.getCountOfBookedForClosedSessionSlot(sessionSlotId)
      UNKNOWN -> throw IllegalStateException("Cant acquire a book count for an UNKNOWN restriction")
    }
  }

  private fun createBooking(application: Application, existingBooking: Visit?): Visit {
    val visitRoom = sessionTemplateService.getVisitRoom(application.sessionSlot.sessionTemplateReference!!)

    val notSavedBooking = existingBooking?.let {
      validateVisitStartDate(it, "changed")
      handleVisitUpdateEvents(it, application)

      // Update existing booking
      it.sessionSlotId = application.sessionSlotId
      it.sessionSlot = application.sessionSlot
      it.visitType = application.visitType
      it.visitRestriction = application.restriction
      it.visitRoom = visitRoom
      it.visitStatus = BOOKED
      it
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
        userType = application.userType,
      )
    }

    val booking = visitRepository.saveAndFlush(notSavedBooking)

    if (hasNotBeenAddedToBooking(booking, application)) {
      booking.addApplication(application)
    }

    application.visitContact?.let {
      booking.visitContact?.let { visitContact ->
        visitContact.name = it.name
        visitContact.telephone = it.telephone
      } ?: run {
        booking.visitContact = VisitContact(
          visit = booking,
          visitId = booking.id,
          name = it.name,
          telephone = it.telephone,
        )
      }
    }

    application.support?.let { applicationSupport ->
      booking.support?.let {
        it.description = applicationSupport.description
      } ?: run {
        booking.support = VisitSupport(visit = booking, visitId = booking.id, description = applicationSupport.description)
      }
    } ?: run {
      booking.support = null
    }

    application.visitors.let {
      booking.visitors.clear()
      visitRepository.saveAndFlush(booking)
      it.map { applicationVisitor ->
        with(applicationVisitor) {
          booking.visitors.add(VisitVisitor(visit = booking, visitId = booking.id, nomisPersonId = nomisPersonId, visitContact = contact))
        }
      }
    }

    return visitRepository.saveAndFlush(booking)
  }

  private fun hasNotBeenAddedToBooking(booking: Visit, application: Application): Boolean {
    return if (booking.getApplications().isEmpty()) true else booking.getApplications().any { it.id == application.id }
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    if (visitRepository.isBookingCancelled(reference)) {
      // If already cancelled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been cancelled!")
      val cancelledVisit = visitRepository.findByReference(reference)!!
      return visitDtoBuilder.build(cancelledVisit)
    }

    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Visit $reference not found")
    validateCancelRequest(visitEntity)

    val cancelOutcome = cancelVisitDto.cancelOutcome

    visitEntity.visitStatus = CANCELLED
    visitEntity.outcomeStatus = cancelOutcome.outcomeStatus

    cancelOutcome.text?.let {
      visitEntity.visitNotes.add(createVisitNote(visitEntity, VisitNoteType.VISIT_OUTCOMES, cancelOutcome.text))
    }

    return processCancelEvents(visitRepository.saveAndFlush(visitEntity), cancelVisitDto)
  }

  @Transactional(readOnly = true)
  fun findVisitsByFilterPageableDescending(visitFilter: VisitFilter, pageablePage: Int? = null, pageableSize: Int? = null): Page<VisitDto> {
    if (visitFilter.prisonCode == null && visitFilter.prisonerId == null) {
      throw ValidationException("Must have prisonId or prisonerId")
    }

    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS)
    return findVisitsOrderByDateAndTime(visitFilter, pageable = page).map { visitDtoBuilder.build(it) }
  }

  private fun findVisitsOrderByDateAndTime(visitFilter: VisitFilter, pageable: Pageable): Page<Visit> {
    return visitRepository.findVisitsOrderByDateAndTime(
      prisonerId = visitFilter.prisonerId,
      prisonCode = visitFilter.prisonCode,
      visitStatusList = visitFilter.visitStatusList.ifEmpty { null },
      visitStartDate = visitFilter.visitStartDate,
      visitEndDate = visitFilter.visitEndDate,
      pageable = pageable,
    )
  }

  @Transactional(readOnly = true)
  fun findVisitsBySessionTemplateFilterPageableDescending(
    sessionTemplateReference: String?,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatusList: List<VisitStatus>,
    visitRestrictions: List<VisitRestriction>?,
    prisonCode: String,
    pageablePage: Int? = null,
    pageableSize: Int? = null,
  ): Page<VisitDto> {
    val page: Pageable =
      PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS, Sort.by(Visit::createTimestamp.name).descending())

    val results = if (sessionTemplateReference != null) {
      visitRepository.findVisitsBySessionTemplateReference(
        sessionTemplateReference = sessionTemplateReference,
        fromDate = fromDate,
        toDate = toDate,
        visitStatusList = visitStatusList.ifEmpty { null },
        visitRestrictions = visitRestrictions,
        prisonCode = prisonCode,
        page,
      )
    } else {
      visitRepository.findVisitsWithNoSessionTemplateReference(
        fromDate = fromDate,
        toDate = toDate,
        visitStatusList = visitStatusList.ifEmpty { null },
        visitRestrictions = visitRestrictions,
        prisonCode = prisonCode,
        page,
      )
    }

    return results.map { visitDtoBuilder.build(it) }
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
    booking: Visit,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val bookedVisitDto = visitDtoBuilder.build(booking)

    try {
      eventAuditRepository.updateVisitApplication(bookedVisitDto.applicationReference, booking.reference, bookingRequestDto.applicationMethodType)
    } catch (e: InvocationTargetException) {
      val message = "Audit log does not exist for ${bookedVisitDto.applicationReference}"
      LOG.error(message)
    }

    val eventAuditDto = visitEventAuditService.saveBookingEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      BOOKED_VISIT,
      bookingRequestDto.applicationMethodType,
      userType = bookedVisitDto.userType,
    )

    telemetryClientService.trackBookingEvent(bookingRequestDto, bookedVisitDto, eventAuditDto)

    snsService.sendVisitBookedEvent(bookedVisitDto)

    return bookedVisitDto
  }

  private fun processUpdateBookingEvents(
    booking: Visit,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val bookedVisitDto = visitDtoBuilder.build(booking)

    try {
      eventAuditRepository.updateVisitApplication(bookedVisitDto.applicationReference, booking.reference, bookingRequestDto.applicationMethodType)
    } catch (e: InvocationTargetException) {
      val message = "Audit log does not exist for ${bookedVisitDto.applicationReference}"
      LOG.error(message)
    }

    val eventAuditDto = visitEventAuditService.saveBookingEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      UPDATED_VISIT,
      bookingRequestDto.applicationMethodType,
      userType = bookedVisitDto.userType,
    )

    telemetryClientService.trackUpdateBookingEvent(bookingRequestDto, bookedVisitDto, eventAuditDto)
    snsService.sendChangedVisitBookedEvent(bookedVisitDto)

    return bookedVisitDto
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
    cancelVisitDto: CancelVisitDto,
  ): VisitDto {
    val visitDto = visitDtoBuilder.build(visit)
    val eventAudit = visitEventAuditService.saveCancelledEventAudit(cancelVisitDto, visitDto)

    telemetryClientService.trackCancelBookingEvent(visitDto, cancelVisitDto, eventAudit)

    snsService.sendVisitCancelledEvent(visitDto)

    // delete all visit notifications for the cancelled visit from the visit notifications table
    visitNotificationEventService.deleteVisitNotificationEvents(visitDto.reference, null, UnFlagEventReason.VISIT_CANCELLED)

    return visitDto
  }

  fun getBookedVisitsForDate(prisonCode: String, date: LocalDate): List<VisitDto> {
    return visitRepository.findBookedVisitsForDate(prisonCode, date).map { visitDtoBuilder.build(it) }
  }

  fun getBookedVisits(
    prisonerNumber: String,
    prisonCode: String,
    visitDate: LocalDate,
  ): List<VisitDto> {
    return visitRepository.findBookedVisits(
      prisonerId = prisonerNumber,
      prisonCode = prisonCode,
      visitDate = visitDate,
    ).map { visitDtoBuilder.build(it) }
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    val visitEntity = visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found")
    return visitDtoBuilder.build(visitEntity)
  }

  @Transactional(readOnly = true)
  fun getBookedVisitByReference(reference: String): VisitDto {
    val visitEntity = visitRepository.findBookedVisit(reference) ?: throw VisitNotFoundException("Booked visit with reference $reference not found")
    return visitDtoBuilder.build(visitEntity)
  }

  @Transactional(readOnly = true)
  fun getHistoryByReference(bookingReference: String): List<EventAuditDto> {
    return eventAuditRepository.findByBookingReferenceOrderById(bookingReference).map { EventAuditDto(it) }
  }

  private fun validateVisitStartDate(
    visit: Visit,
    action: String,
    allowedVisitStartDate: LocalDateTime = LocalDateTime.now(),
  ) {
    if (visit.sessionSlot.slotStart.isBefore(allowedVisitStartDate)) {
      throw ExpiredVisitAmendException(
        AMEND_EXPIRED_ERROR_MESSAGE.format(visit.reference, action),
        ExpiredVisitAmendException("trying to change / cancel an expired visit"),
      )
    }
  }

  private fun handleVisitUpdateEvents(existingBooking: Visit, application: Application) {
    if (existingBooking.sessionSlot.slotDate != application.sessionSlot.slotDate) {
      visitNotificationEventService.deleteVisitNotificationEvents(existingBooking.reference, NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE, UnFlagEventReason.VISIT_DATE_UPDATED)
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

  fun getFutureVisitsBy(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> {
    return visitRepository.getVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }
  }

  fun getFutureVisitsByVisitorId(
    visitorId: String,
    prisonerId: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> {
    return visitRepository.getFutureVisitsByVisitorId(visitorId, prisonerId, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }
  }

  fun getFutureBookedVisitsExcludingPrison(
    prisonerNumber: String,
    excludedPrisonCode: String,
  ): List<VisitDto> {
    return this.visitRepository.getFutureBookedVisitsExcludingPrison(prisonerNumber, excludedPrisonCode).map { visitDtoBuilder.build(it) }
  }

  fun findFutureVisitsBySessionPrisoner(prisonerNumber: String): List<VisitDto> {
    return getFutureVisitsBy(prisonerNumber = prisonerNumber)
  }

  fun getFuturePublicBookedVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    val bookingReferenceList = visitRepository.getPublicFutureBookingsByBookerReference(bookerReference)
    return visitRepository.findVisitsByReferences(bookingReferenceList).map { visitDtoBuilder.build(it) }.sortedBy { it.startTimestamp }
  }

  fun getPublicCanceledVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    val bookingReferenceList = visitRepository.getPublicCanceledVisitsByBookerReference(bookerReference)
    return visitRepository.findVisitsByReferences(bookingReferenceList).map { visitDtoBuilder.build(it) }.sortedByDescending { it.modifiedTimestamp }
  }

  fun getPublicPastVisitsByBookerReference(bookerReference: String): List<VisitDto> {
    val bookingReferenceList = visitRepository.getPublicPastBookingsByBookerReference(bookerReference)
    return visitRepository.findVisitsByReferences(bookingReferenceList).map { visitDtoBuilder.build(it) }.sortedByDescending { it.startTimestamp }
  }
}
