package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class VisitService(
  private val visitRepository: VisitRepository,
  private val visitStoreService: VisitStoreService,
  private val telemetryClientService: TelemetryClientService,
  private val eventAuditService: VisitEventAuditService,
  private val snsService: SnsService,
  private val applicationValidationService: ApplicationValidationService,
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

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
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

    val booking = visitStoreService.createOrUpdateBooking(application, existingBooking)

    return existingBooking?.let {
      processUpdateBookingEvents(booking, bookingRequestDto)
    } ?: run {
      processBookingEvents(booking, bookingRequestDto)
    }
  }

  @Transactional
  fun getBookCountForSlot(sessionSlotId: Long, restriction: VisitRestriction): Long {
    return when (restriction) {
      OPEN -> visitRepository.getCountOfBookedForOpenSessionSlot(sessionSlotId)
      CLOSED -> visitRepository.getCountOfBookedForClosedSessionSlot(sessionSlotId)
      UNKNOWN -> throw IllegalStateException("Cant acquire a book count for an UNKNOWN restriction")
    }
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val cancelledVisit = visitStoreService.cancelVisit(reference, cancelVisitDto)
    return processCancelEvents(cancelledVisit, cancelVisitDto)
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

  private fun processBookingEvents(
    booking: Visit,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val bookedVisitDto = visitDtoBuilder.build(booking)

    val bookingEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveBookingEvent(bookedVisitDto, bookingRequestDto)

    telemetryClientService.trackBookingEvent(bookingRequestDto, bookedVisitDto, bookingEventAuditDto)

    snsService.sendVisitBookedEvent(bookedVisitDto)

    return bookedVisitDto
  }

  private fun processUpdateBookingEvents(
    booking: Visit,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val bookedVisitDto = visitDtoBuilder.build(booking)

    val updatedEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveUpdatedEvent(bookedVisitDto, bookingRequestDto)

    telemetryClientService.trackUpdateBookingEvent(bookingRequestDto, bookedVisitDto, updatedEventAuditDto)

    snsService.sendChangedVisitBookedEvent(bookedVisitDto)

    return bookedVisitDto
  }

  private fun processCancelEvents(
    visitDto: VisitDto,
    cancelVisitDto: CancelVisitDto,
  ): VisitDto {

    val cancelledEventAuditDto = visitEventAuditService.saveCancelledEventAudit(cancelVisitDto, visitDto)

    telemetryClientService.trackCancelBookingEvent(visitDto, cancelVisitDto, cancelledEventAuditDto)

    snsService.sendVisitCancelledEvent(visitDto)

    // delete all visit notifications for the cancelled visit from the visit notifications table
    visitNotificationEventService.deleteVisitNotificationEvents(visitDto.reference, null, UnFlagEventReason.VISIT_CANCELLED)

    return visitDto
  }

  @Transactional
  fun getBookedVisitsForDate(prisonCode: String, date: LocalDate): List<VisitDto> {
    return visitRepository.findBookedVisitsForDate(prisonCode, date).map { visitDtoBuilder.build(it) }
  }

  @Transactional
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
    return eventAuditService.findByBookingReferenceOrderById(bookingReference)
  }

  @Transactional
  fun getFutureVisitsBy(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> {
    return visitRepository.getVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }
  }

  @Transactional
  fun getFutureVisitsByVisitorId(
    visitorId: String,
    prisonerId: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> {
    return visitRepository.getFutureVisitsByVisitorId(visitorId, prisonerId, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }
  }

  @Transactional
  fun getFutureBookedVisitsExcludingPrison(
    prisonerNumber: String,
    excludedPrisonCode: String,
  ): List<VisitDto> {
    return this.visitRepository.getFutureBookedVisitsExcludingPrison(prisonerNumber, excludedPrisonCode).map { visitDtoBuilder.build(it) }
  }

  @Transactional
  fun findFutureVisitsBySessionPrisoner(prisonerNumber: String): List<VisitDto> {
    return getFutureVisitsBy(prisonerNumber = prisonerNumber)
  }
}
