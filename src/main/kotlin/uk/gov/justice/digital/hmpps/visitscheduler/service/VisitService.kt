package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrivatePrisonVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
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
) {

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  @Lazy
  @Autowired
  private lateinit var visitNotificationEventService: VisitNotificationEventService

  @Autowired
  private lateinit var visitDtoBuilder: VisitDtoBuilder

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    const val MAX_RECORDS = 10000
  }

  fun bookVisit(applicationReference: String, bookingRequestDto: BookingRequestDto): VisitDto {
    val alreadyBookedVisit = visitStoreService.checkBookingAlreadyMade(applicationReference)
    alreadyBookedVisit?.let {
      return alreadyBookedVisit
    }

    val booking = visitStoreService.createOrUpdateBooking(applicationReference, bookingRequestDto)
    return processBookingEvents(booking, bookingRequestDto)
  }

  fun bookPrivatePrisonVisit(privatePrisonVisitDto: PrivatePrisonVisitDto): Long {
    val booking = visitStoreService.createPrivatePrisonVisit(privatePrisonVisitDto)

  }

  fun updateBookedVisit(applicationReference: String, bookingRequestDto: BookingRequestDto): VisitDto {
    val alreadyBookedVisit = visitStoreService.checkBookingAlreadyMade(applicationReference)
    alreadyBookedVisit?.let {
      return alreadyBookedVisit
    }

    val existingVisit = visitStoreService.getBookingByApplicationReference(applicationReference)
    val booking = visitStoreService.createOrUpdateBooking(applicationReference, bookingRequestDto)
    return processUpdateBookingEvents(existingVisit, booking, bookingRequestDto)
  }

  @Transactional
  fun getBookedVisitByApplicationReference(applicationReference: String): VisitDto {
    val visit = visitRepository.findVisitByApplicationReference(applicationReference)
    visit?.let {
      return visitDtoBuilder.build(visit)
    } ?: throw VisitNotFoundException("Visit not found for application reference")
  }

  @Transactional
  fun getBookCountForSlot(sessionSlotId: Long, restriction: VisitRestriction): Long = when (restriction) {
    OPEN -> visitRepository.getCountOfBookedForOpenSessionSlot(sessionSlotId)
    CLOSED -> visitRepository.getCountOfBookedForClosedSessionSlot(sessionSlotId)
    UNKNOWN -> throw IllegalStateException("Cant acquire a book count for an UNKNOWN restriction")
  }

  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    val alreadyCancelledVisit = visitStoreService.checkBookingAlreadyCancelled(reference)
    alreadyCancelledVisit?.let {
      return alreadyCancelledVisit
    }

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

  private fun findVisitsOrderByDateAndTime(visitFilter: VisitFilter, pageable: Pageable): Page<Visit> = visitRepository.findVisitsOrderByDateAndTime(
    prisonerId = visitFilter.prisonerId,
    prisonCode = visitFilter.prisonCode,
    visitStatusList = visitFilter.visitStatusList.ifEmpty { null },
    visitStartDate = visitFilter.visitStartDate,
    visitEndDate = visitFilter.visitEndDate,
    pageable = pageable,
  )

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
    val page: Pageable = PageRequest.of(pageablePage ?: 0, pageableSize ?: MAX_RECORDS)

    val results = if (sessionTemplateReference != null) {
      visitRepository.findVisitsBySessionTemplateReference(
        sessionTemplateReference = sessionTemplateReference,
        fromDate = fromDate,
        toDate = toDate,
        visitStatusList = visitStatusList.ifEmpty { null },
        visitRestrictions = visitRestrictions,
        prisonCode = prisonCode,
      )
    } else {
      visitRepository.findVisitsWithNoSessionTemplateReference(
        fromDate = fromDate,
        toDate = toDate,
        visitStatusList = visitStatusList.ifEmpty { null },
        visitRestrictions = visitRestrictions,
        prisonCode = prisonCode,
      )
    }

    val visits = results.map {
      visitDtoBuilder.build(it).also { visitDto ->
        setFirstBookedDateTime(visitDto)
      }
    }.sortedWith(compareByDescending(nullsFirst()) { it.firstBookedDateTime })

    return PageImpl(visits, page, visits.size.toLong())
  }

  private fun setFirstBookedDateTime(visitDto: VisitDto) {
    visitDto.firstBookedDateTime = eventAuditService.getLastEventForBookingOrMigration(visitDto.reference)?.createTimestamp
  }

  private fun processBookingEvents(
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val bookingEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveEvent(bookedVisitDto, bookingRequestDto, EventAuditType.BOOKED_VISIT)

    telemetryClientService.trackBookingEvent(bookingRequestDto, bookedVisitDto, bookingEventAuditDto)

    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
      bookedVisitDto.reference,
      bookedVisitDto.createdTimestamp,
      bookedVisitDto.modifiedTimestamp,
      bookedVisitDto.prisonerId,
      bookingEventAuditDto.id,
    )
    snsService.sendVisitBookedEvent(snsDomainEventPublishDto)

    return bookedVisitDto
  }

  private fun processPrivatePrisonBookingEvents(
    privatePrisonVisitDto: PrivatePrisonVisitDto,
    bookingRequestDto: BookingRequestDto,
  ): PrivatePrisonVisitDto {
    // id from after or before with client idx
    val createdVisitEventAuditDto = visitEventAuditService.savePrivatePrisonVisitEventAudit(privatePrisonVisitDto, EventAuditType.BOOKED_VISIT)

    telemetryClientService.trackPrivatePrisonBookingEvent(privatePrisonVisitDto, createdVisitEventAuditDto)

//    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
//      bookedVisitDto.clientVisitReference,
//      bookedVisitDto,
//      bookedVisitDto.,
//      bookedVisitDto.prisonerId,
//      createdVisitEventAuditDto.id,
//    )
//    snsService.sendVisitBookedEvent(snsDomainEventPublishDto)

    return privatePrisonVisitDto
  }

  private fun processUpdateBookingEvents(
    visitDtoBeforeUpdate: VisitDto?,
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val updatedEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveEvent(bookedVisitDto, bookingRequestDto, EventAuditType.UPDATED_VISIT)

    telemetryClientService.trackUpdateBookingEvent(visitDtoBeforeUpdate, bookingRequestDto, bookedVisitDto, updatedEventAuditDto)

    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
      bookedVisitDto.reference,
      bookedVisitDto.createdTimestamp,
      bookedVisitDto.modifiedTimestamp,
      bookedVisitDto.prisonerId,
      updatedEventAuditDto.id,
    )
    snsService.sendChangedVisitBookedEvent(snsDomainEventPublishDto)

    return bookedVisitDto
  }

  private fun processCancelEvents(
    visitDto: VisitDto,
    cancelVisitDto: CancelVisitDto,
  ): VisitDto {
    val cancelledEventAuditDto = visitEventAuditService.saveCancelledEventAudit(cancelVisitDto, visitDto)

    telemetryClientService.trackCancelBookingEvent(visitDto, cancelVisitDto, cancelledEventAuditDto)

    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
      visitDto.reference,
      visitDto.createdTimestamp,
      visitDto.modifiedTimestamp,
      visitDto.prisonerId,
      cancelledEventAuditDto.id,
    )
    snsService.sendVisitCancelledEvent(snsDomainEventPublishDto)

    // delete all visit notifications and any paired notifications for the cancelled visit from the visit notifications table
    visitNotificationEventService.deleteVisitAndPairedNotificationEvents(visitDto.reference, UnFlagEventReason.VISIT_CANCELLED)

    return visitDto
  }

  @Transactional(readOnly = true)
  fun getBookedVisitsForDate(prisonCode: String, date: LocalDate): List<VisitDto> = visitRepository.findBookedVisitsForDate(prisonCode, date).map { visitDtoBuilder.build(it) }

  @Transactional(readOnly = true)
  fun getBookedVisitsBySessionForDate(sessionTemplateReference: String, date: LocalDate): List<VisitDto> = visitRepository.findBookedVisitsBySessionForDate(sessionTemplateReference, date).map { visitDtoBuilder.build(it) }

  @Transactional(readOnly = true)
  fun getBookedVisits(
    prisonerNumber: String,
    prisonCode: String,
    visitDate: LocalDate,
  ): List<VisitDto> = visitRepository.findBookedVisits(
    prisonerId = prisonerNumber,
    prisonCode = prisonCode,
    visitDate = visitDate,
  ).map { visitDtoBuilder.build(it) }

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
  fun getHistoryByReference(bookingReference: String): List<EventAuditDto> = eventAuditService.findByBookingReferenceOrderById(bookingReference)

  @Transactional(readOnly = true)
  fun getFutureVisitsBy(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> = visitRepository.getVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

  @Transactional(readOnly = true)
  fun getFutureBookedVisits(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> = visitRepository.getBookedVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

  @Transactional
  fun getFutureVisitsByVisitorId(
    visitorId: String,
    prisonerId: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> = visitRepository.getFutureVisitsByVisitorId(visitorId, prisonerId, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

  @Transactional
  fun getFutureBookedVisitsExcludingPrison(
    prisonerNumber: String,
    excludedPrisonCode: String,
  ): List<VisitDto> = this.visitRepository.getFutureBookedVisitsExcludingPrison(prisonerNumber, excludedPrisonCode).map { visitDtoBuilder.build(it) }

  @Transactional
  fun findFutureVisitsBySessionPrisoner(prisonerNumber: String): List<VisitDto> = getFutureVisitsBy(prisonerNumber = prisonerNumber)
}
