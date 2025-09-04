package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitPreviewDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.diff.UpdateVisitSummaryUtil
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class VisitService(
  private val visitRepository: VisitRepository,
  private val visitStoreService: VisitStoreService,
  private val telemetryClientService: TelemetryClientService,
  private val eventAuditService: VisitEventAuditService,
  private val snsService: SnsService,
  private val updateVisitSummaryUtil: UpdateVisitSummaryUtil,
  @Value("\${feature.request-booking-enabled:false}") private val requestBookingFeatureEnabled: Boolean,
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

  fun createVisitFromExternalSystem(createVisitFromExternalSystemDto: CreateVisitFromExternalSystemDto): VisitDto {
    val booking = visitStoreService.createVisitFromExternalSystem(createVisitFromExternalSystemDto)

    return processCreateVisitFromExternalSystemEvents(booking, createVisitFromExternalSystemDto)
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
  ): Page<VisitPreviewDto> {
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

    val visits = results
      .map { visit ->
        VisitPreviewDto(
          visit,
          eventAuditService.getLastEventForBookingOrMigration(visit.reference)?.createTimestamp,
        )
      }
      .sortedWith(compareByDescending(nullsFirst()) { it.firstBookedDateTime })

    return PageImpl(visits, page, visits.size.toLong())
  }

  private fun processBookingEvents(
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val eventType = if (requestBookingFeatureEnabled) {
      if (bookingRequestDto.isRequestBooking == true) {
        EventAuditType.REQUESTED_VISIT
      } else {
        EventAuditType.BOOKED_VISIT
      }
    } else {
      EventAuditType.BOOKED_VISIT
    }

    val bookingEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveEvent(bookedVisitDto, bookingRequestDto, eventType, text = null)

    telemetryClientService.trackBookingEvent(bookedVisitDto, bookingEventAuditDto, isRequestBooking = bookingRequestDto.isRequestBooking == true)

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

  private fun processCreateVisitFromExternalSystemEvents(
    bookedVisitDto: VisitDto,
    createVisitFromExternalSystemDto: CreateVisitFromExternalSystemDto,
  ): VisitDto {
    val bookingEventAuditDto = visitEventAuditService.saveBookingEventAudit(createVisitFromExternalSystemDto.prisonerId, bookedVisitDto, EventAuditType.BOOKED_VISIT, ApplicationMethodType.BY_PRISONER, UserType.PRISONER)

    telemetryClientService.trackBookingEvent(bookedVisitDto, bookingEventAuditDto, isRequestBooking = false)

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

  private fun processUpdateVisitFromExternalSystemEvents(
    existingVisitDto: VisitDto,
    updatedVisitDto: VisitDto,
  ): VisitDto {
    val bookingEventAuditDto = visitEventAuditService.saveBookingEventAudit(updatedVisitDto.prisonerId, updatedVisitDto, EventAuditType.UPDATED_VISIT, ApplicationMethodType.BY_PRISONER, UserType.PRISONER)

    telemetryClientService.trackUpdateBookingEvent(existingVisitDto, updatedVisitDto, bookingEventAuditDto)

    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
      updatedVisitDto.reference,
      updatedVisitDto.createdTimestamp,
      updatedVisitDto.modifiedTimestamp,
      updatedVisitDto.prisonerId,
      bookingEventAuditDto.id,
    )
    snsService.sendChangedVisitBookedEvent(snsDomainEventPublishDto)

    return updatedVisitDto
  }

  private fun processUpdateBookingEvents(
    visitDtoBeforeUpdate: VisitDto?,
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
  ): VisitDto {
    val updateText = visitDtoBeforeUpdate?.let {
      updateVisitSummaryUtil.getDiff(visitDtoAfterUpdate = bookedVisitDto, visitDtoBeforeUpdate = visitDtoBeforeUpdate)
    }
    val updatedEventAuditDto = visitEventAuditService.updateVisitApplicationAndSaveEvent(bookedVisitDto, bookingRequestDto, EventAuditType.UPDATED_VISIT, text = updateText)

    telemetryClientService.trackUpdateBookingEvent(visitDtoBeforeUpdate, bookedVisitDto, updatedEventAuditDto)

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
    val unFlagEventReason = if (visitDto.visitSubStatus == VisitSubStatus.WITHDRAWN) {
      UnFlagEventReason.REQUESTED_VISIT_WITHDRAWN
    } else {
      UnFlagEventReason.VISIT_CANCELLED
    }
    visitNotificationEventService.deleteVisitAndPairedNotificationEvents(visitDto.reference, reason = unFlagEventReason)

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
  fun getVisitsThatOverlapProvidedTimeWindow(
    prisonerNumber: String,
    prisonCode: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
  ): List<VisitDto> = visitRepository.getVisitsInTimeWindow(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

  @Transactional(readOnly = true)
  fun getFutureBookedVisits(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> = visitRepository.getBookedVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

  @Transactional(readOnly = true)
  fun getFutureBookedVisitsExcludingRequestVisits(
    prisonerNumber: String,
    prisonCode: String? = null,
    startDateTime: LocalDateTime = LocalDateTime.now(),
    endDateTime: LocalDateTime? = null,
  ): List<VisitDto> = visitRepository.getBookedVisitsExcludingRequestVisits(prisonerNumber, prisonCode, startDateTime, endDateTime).map { visitDtoBuilder.build(it) }

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

  @Transactional
  fun getVisitReferenceByClientReference(clientReference: String): List<String> {
    val visitReference = visitRepository.getVisitReferenceByExternalSystemClientReference(clientReference)
    if (visitReference.isEmpty()) {
      throw VisitNotFoundException("Visit not found for external client reference")
    }
    return visitReference
  }

  @Transactional
  fun updateVisitFromExternalSystem(
    bookingReference: String,
    updateVisitFromExternalSystemDto: UpdateVisitFromExternalSystemDto,
  ): VisitDto {
    val existingVisit =
      visitRepository.findBookedVisit(bookingReference) ?: throw VisitNotFoundException("Visit $bookingReference not found")
    val existingVisitDto = visitDtoBuilder.build(existingVisit)

    val updatedVisitDto = visitStoreService.updateVisitFromExternalSystem(updateVisitFromExternalSystemDto, existingVisit)

    return processUpdateVisitFromExternalSystemEvents(existingVisitDto, updatedVisitDto)
  }
}
