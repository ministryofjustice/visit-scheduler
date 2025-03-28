package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.NotifyHistoryDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_APPLICABLE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.IGNORE_VISIT_NOTIFICATIONS_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.MIGRATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PRISONER
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ActionedByRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import java.lang.reflect.InvocationTargetException
import java.time.LocalDateTime

@Service
@Transactional
class VisitEventAuditService(private val notifyHistoryDtoBuilder: NotifyHistoryDtoBuilder) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Lazy
  @Autowired
  private lateinit var actionedByRepository: ActionedByRepository

  @Lazy
  @Autowired
  private lateinit var eventAuditRepository: EventAuditRepository

  fun saveApplicationEventAudit(
    actionedByValue: String,
    application: ApplicationDto,
    visit: Visit? = null,
    applicationMethodType: ApplicationMethodType,
  ): EventAuditDto {
    val actionedBy = createOrGetActionBy(actionedByValue, application.userType)

    val eventAudit = eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visit?.reference,
        applicationReference = application.reference,
        sessionTemplateReference = application.sessionTemplateReference,
        type = if (application.reserved) RESERVED_VISIT else CHANGING_VISIT,
        applicationMethodType = applicationMethodType,
        text = null,
      ),
    )

    actionedBy.eventAuditList.add(eventAudit)
    actionedByRepository.saveAndFlush(actionedBy)

    return EventAuditDto(eventAudit, notifyHistoryDtoBuilder)
  }

  fun saveBookingEventAudit(
    actionedByValue: String,
    visit: VisitDto,
    type: EventAuditType,
    applicationMethodType: ApplicationMethodType,
    userType: UserType,
  ): EventAuditDto {
    val actionedBy = createOrGetActionBy(actionedByValue, userType)

    return EventAuditDto(
      eventAuditRepository.saveAndFlush(
        EventAudit(
          actionedBy = actionedBy,
          bookingReference = visit.reference,
          applicationReference = visit.applicationReference,
          sessionTemplateReference = visit.sessionTemplateReference,
          type = type,
          applicationMethodType = applicationMethodType,
          text = null,
        ),
      ),
      notifyHistoryDtoBuilder,
    )
  }

  fun saveIgnoreVisitNotificationEventAudit(
    actionedByValue: String,
    visit: VisitDto,
    text: String,
  ): EventAuditDto {
    val actionedBy = createOrGetActionBy(actionedByValue, STAFF)

    return EventAuditDto(
      eventAuditRepository.saveAndFlush(
        EventAudit(
          actionedBy = actionedBy,
          bookingReference = visit.reference,
          applicationReference = visit.applicationReference,
          sessionTemplateReference = visit.sessionTemplateReference,
          type = IGNORE_VISIT_NOTIFICATIONS_EVENT,
          applicationMethodType = NOT_APPLICABLE,
          text = text,
        ),
      ),
      notifyHistoryDtoBuilder,
    )
  }

  fun saveCancelledEventAudit(cancelVisitDto: CancelVisitDto, visit: VisitDto): EventAuditDto = saveCancelledEventAudit(cancelVisitDto.actionedBy, cancelVisitDto.userType, cancelVisitDto.applicationMethodType, visit)

  fun saveCancelledMigratedEventAudit(cancelVisitDto: MigratedCancelVisitDto, visit: VisitDto): EventAuditDto = saveCancelledEventAudit(cancelVisitDto.actionedBy, STAFF, NOT_KNOWN, visit)

  fun saveMigratedVisitEventAudit(
    migrateVisitRequest: MigrateVisitRequestDto,
    visitEntity: Visit,
  ): EventAudit {
    val actionedBy = createOrGetActionBy(migrateVisitRequest.actionedBy, STAFF)

    val eventAudit = eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visitEntity.reference,
        applicationReference = visitEntity.getLastCompletedApplication()!!.reference,
        sessionTemplateReference = visitEntity.sessionSlot.sessionTemplateReference,
        type = MIGRATED_VISIT,
        applicationMethodType = NOT_KNOWN,
        text = null,
      ),
    )

    return eventAudit
  }

  fun updateCreateTimestamp(time: LocalDateTime, eventAudit: EventAudit): EventAuditDto {
    eventAuditRepository.updateCreateTimestamp(time, eventAudit.id)
    return EventAuditDto(eventAudit, notifyHistoryDtoBuilder)
  }

  fun saveNotificationEventAudit(type: NotificationEventType, impactedVisit: VisitDto): EventAuditDto {
    val actionedBy = createOrGetActionBy(null, SYSTEM)
    return EventAuditDto(
      eventAuditRepository.saveAndFlush(
        EventAudit(
          actionedBy = actionedBy,
          bookingReference = impactedVisit.reference,
          applicationReference = impactedVisit.applicationReference,
          sessionTemplateReference = impactedVisit.sessionTemplateReference,
          type = EventAuditType.valueOf(type.name),
          applicationMethodType = NOT_KNOWN,
          text = null,
        ),
      ),
      notifyHistoryDtoBuilder,
    )
  }

  @Transactional(readOnly = true)
  fun getLastEventForBooking(bookingReference: String): EventAuditDto? = eventAuditRepository.findLastBookedVisitEventByBookingReference(bookingReference)?.let {
    EventAuditDto(it, notifyHistoryDtoBuilder)
  }

  @Transactional(readOnly = true)
  fun getLastEventForBookingOrMigration(bookingReference: String): EventAuditDto? = eventAuditRepository.findLastBookedOrMigratedVisitEventByBookingReference(bookingReference)?.let {
    EventAuditDto(it, notifyHistoryDtoBuilder)
  }

  @Transactional(readOnly = true)
  fun getLastUserToUpdateSlotByReference(bookingReference: String): ActionedBy = eventAuditRepository.getLastUserToUpdateBookingByReference(bookingReference)

  fun updateVisitApplicationAndSaveEvent(
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
    eventType: EventAuditType,
  ): EventAuditDto {
    if (bookedVisitDto.applicationReference == null) {
      // This should not happen as application reference should always be set here
      throw IllegalArgumentException("Visit application reference is not set")
    }
    try {
      eventAuditRepository.updateVisitApplication(bookedVisitDto.applicationReference, bookedVisitDto.reference, bookingRequestDto.applicationMethodType)
    } catch (e: InvocationTargetException) {
      LOG.error("Audit log does not exist for ${bookedVisitDto.applicationReference}")
    }

    return saveBookingEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      eventType,
      bookingRequestDto.applicationMethodType,
      userType = bookingRequestDto.userType,
    )
  }

  fun findByBookingReferenceOrderById(bookingReference: String): List<EventAuditDto> = eventAuditRepository.findByBookingReferenceOrderById(bookingReference).map { EventAuditDto(it, notifyHistoryDtoBuilder) }

  private fun saveCancelledEventAudit(actionedByValue: String, userType: UserType, applicationMethodType: ApplicationMethodType, visit: VisitDto): EventAuditDto {
    val actionedBy = createOrGetActionBy(actionedByValue, userType)

    return EventAuditDto(
      eventAuditRepository.saveAndFlush(
        EventAudit(
          actionedBy = actionedBy,
          bookingReference = visit.reference,
          applicationReference = visit.applicationReference,
          sessionTemplateReference = visit.sessionTemplateReference,
          type = CANCELLED_VISIT,
          applicationMethodType = applicationMethodType,
          text = null,
        ),
      ),
      notifyHistoryDtoBuilder,
    )
  }

  fun savePairedVisitChangeEventAudit(
    visitEntity: Visit,
    type: EventAuditType,
    auditText: String,
  ): EventAudit {
    val actionedBy = createOrGetActionBy(null, SYSTEM)

    val eventAudit = eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = visitEntity.reference,
        applicationReference = null,
        sessionTemplateReference = null,
        type = type,
        applicationMethodType = NOT_APPLICABLE,
        text = auditText,
      ),
    )

    return eventAudit
  }

  private fun createOrGetActionBy(actionedByValue: String? = null, userType: UserType): ActionedBy {
    var bookerReference: String? = null
    var userName: String? = null

    val actionedBy = when (userType) {
      PUBLIC -> {
        bookerReference = actionedByValue!!
        actionedByRepository.findActionedByForPublic(bookerReference)
      }
      STAFF -> {
        userName = actionedByValue!!
        actionedByRepository.findActionedByForStaff(userName)
      }
      SYSTEM -> {
        actionedByRepository.findActionedByForSystem()
      }
      PRISONER -> {
        userName = actionedByValue!!
        actionedByRepository.findActionedByForPrisoner(prisonerId = userName)
      }
    }

    return actionedBy
      ?: actionedByRepository.saveAndFlush(
        ActionedBy(
          bookerReference = bookerReference,
          userName = userName,
          userType = userType,
        ),
      )
  }
}
