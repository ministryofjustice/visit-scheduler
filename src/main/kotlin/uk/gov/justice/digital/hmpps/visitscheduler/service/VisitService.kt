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
import org.springframework.transaction.annotation.Propagation.NEVER
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ExpiredVisitAmendException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.SupportNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SupportTypeRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_BOOKED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.util.PersistVisitHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
@Transactional
class VisitService(
  private val visitRepository: VisitRepository,
  private val supportTypeRepository: SupportTypeRepository,
  private val telemetryClient: TelemetryClient,
  private val sessionTemplateService: SessionTemplateService,
  private val snsService: SnsService,
  private val persistVisitHelper: PersistVisitHelper,
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
    return reserveVisitSlot(bookingReference, reserveVisitSlotDto)
  }

  fun reserveVisitSlot(bookingReference: String = "", reserveVisitSlotDto: ReserveVisitSlotDto): VisitDto {
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

    val visitDto = VisitDto(visitEntity)
    val eventName = if (bookingReference.isBlank()) TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT.eventName else TelemetryVisitEvents.VISIT_CHANGED_EVENT.eventName
    trackEvent(eventName, createVisitTrackEventFromVisitEntity(visitDto, reserveVisitSlotDto.actionedBy))
    return visitDto
  }

  private fun getStartingStatus(
    bookingReference: String,
    reserveVisitSlotDto: ReserveVisitSlotDto,
    sessionTemplate: SessionTemplateDto,
  ): VisitStatus {
    val bookedVisit = this.visitRepository.findBookedVisit(bookingReference)

    if (bookedVisit == null ||
      (bookedVisit.prison.code != sessionTemplate.prisonCode) ||
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

    visitEntity.sessionTemplateReference = changeVisitSlotRequestDto.sessionTemplateReference

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

  @Transactional(propagation = NEVER)
  fun bookVisit(applicationReference: String): VisitDto {
    if (visitRepository.isApplicationBooked(applicationReference)) {
      LOG.debug("The application $applicationReference has already been booked!")
      // If already booked then just return object and do nothing more!
      val bookedApplication = visitRepository.findBookedApplication(applicationReference)!!
      return VisitDto(bookedApplication)
    }

    val bookingReferenceFromApplication = visitRepository.getApplicationBookingReference(applicationReference) ?: throw VisitNotFoundException("Application (reference $applicationReference) not found")
    val hasExistingBooking = visitRepository.doseBookedVisitExist(bookingReferenceFromApplication)
    if (hasExistingBooking) {
      // check if the existing visit is in the past
      validateVisitStartDate(visitRepository.findBookedVisit(bookingReferenceFromApplication)!!, "changed")
    }

    val bookedVisitDto = persistVisitHelper.persistBooking(applicationReference)

    processBookingEvents(bookedVisitDto, hasExistingBooking)

    return bookedVisitDto
  }

  private fun processBookingEvents(
    bookedVisitDto: VisitDto,
    hasExistingBooking: Boolean,
  ) {
    val bookEvent = createVisitTrackEventFromVisitEntity(bookedVisitDto, bookedVisitDto.createdBy)
    bookEvent["isUpdated"] = hasExistingBooking.toString()
    trackEvent(VISIT_BOOKED_EVENT.eventName, bookEvent)

    if (hasExistingBooking) {
      snsService.sendChangedVisitBookedEvent(bookedVisitDto)
    } else {
      snsService.sendVisitBookedEvent(bookedVisitDto)
    }
  }

  @Deprecated("This method has been deprecated.")
  fun cancelVisit(reference: String, outcomeDto: OutcomeDto): VisitDto {
    LOG.debug("Enter legacy call to cancelVisit $reference")
    val cancelVisitDto = CancelVisitDto(outcomeDto, NOT_KNOWN_NOMIS)
    return cancelVisit(reference, cancelVisitDto)
  }

  @Transactional(propagation = NEVER)
  fun cancelVisit(reference: String, cancelVisitDto: CancelVisitDto): VisitDto {
    if (visitRepository.isBookingCancelled(reference)) {
      // If already canceled then just return object and do nothing more!
      LOG.debug("The visit $reference has already been canceled!")
      val canceledVisit = visitRepository.findByReference(reference)!!
      return VisitDto(canceledVisit)
    }

    validateCancelRequest(reference)

    val visitDto = persistVisitHelper.persistCancel(reference, cancelVisitDto)

    processCancelEvents(visitDto, cancelVisitDto)

    return visitDto
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
    visitDto: VisitDto,
    cancelVisitDto: CancelVisitDto,
  ) {
    val eventsMap = createVisitTrackEventFromVisitEntity(visitDto, cancelVisitDto.actionedBy)
    visitDto.outcomeStatus?.let {
      eventsMap.put("outcomeStatus", it.name)
    }
    trackEvent(
      VISIT_CANCELLED_EVENT.eventName,
      eventsMap,
    )
    snsService.sendVisitCancelledEvent(visitDto)
  }

  @Transactional(readOnly = true)
  fun getVisitByReference(reference: String): VisitDto {
    return VisitDto(visitRepository.findByReference(reference) ?: throw VisitNotFoundException("Visit reference $reference not found"))
  }

  @Transactional(readOnly = true)
  fun getVisitHistoryByReference(reference: String): List<VisitDto> {
    return visitRepository.findAllByReference(reference).map { VisitDto(it) }
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

  private fun createVisitTrackEventFromVisitEntity(visitDto: VisitDto, actionedBy: String): MutableMap<String, String> {
    return mutableMapOf(
      "reference" to visitDto.reference,
      "prisonerId" to visitDto.prisonerId,
      "prisonId" to visitDto.prisonCode,
      "visitType" to visitDto.visitType.name,
      "visitRoom" to visitDto.visitRoom,
      "visitRestriction" to visitDto.visitRestriction.name,
      "visitStart" to visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visitDto.visitStatus.name,
      "applicationReference" to visitDto.applicationReference,
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
