package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_APPLICABLE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CANCELLED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.IGNORE_VISIT_NOTIFICATIONS_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.MIGRATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.REQUESTED_VISIT_WITHDRAWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRequestRejectionReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.lang.reflect.InvocationTargetException
import java.time.LocalDateTime

@Service
class VisitEventAuditService(
  private val eventAuditTransactionalService: VisitEventAuditTransactionalService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun saveApplicationEventAudit(
    actionedByValue: String,
    application: ApplicationDto,
    visit: Visit? = null,
    applicationMethodType: ApplicationMethodType,
  ): EventAuditDto = EventAuditDto(
    saveAuditEvent(
      actionedBy = actionedByValue,
      userType = application.userType,
      bookingReference = visit?.reference,
      applicationReference = application.reference,
      sessionTemplateReference = application.sessionTemplateReference,
      eventAuditType = if (application.reserved) RESERVED_VISIT else CHANGING_VISIT,
      applicationMethodType = applicationMethodType,
      text = null,
    ),
  )

  fun saveBookingEventAudit(
    actionedByValue: String,
    visit: VisitDto,
    type: EventAuditType,
    applicationMethodType: ApplicationMethodType,
    userType: UserType,
    text: String? = null,
  ): EventAuditDto = EventAuditDto(
    saveAuditEvent(
      actionedBy = actionedByValue,
      userType = userType,
      bookingReference = visit.reference,
      applicationReference = visit.applicationReference,
      sessionTemplateReference = visit.sessionTemplateReference,
      eventAuditType = type,
      applicationMethodType = applicationMethodType,
      text = text,
    ),
  )

  fun saveIgnoreVisitNotificationEventAudit(
    actionedByValue: String,
    visit: VisitDto,
    text: String,
  ): EventAuditDto = EventAuditDto(
    saveAuditEvent(
      actionedBy = actionedByValue,
      userType = STAFF,
      bookingReference = visit.reference,
      applicationReference = visit.applicationReference,
      sessionTemplateReference = visit.sessionTemplateReference,
      eventAuditType = IGNORE_VISIT_NOTIFICATIONS_EVENT,
      applicationMethodType = NOT_APPLICABLE,
      text = text,
    ),
  )

  fun saveCancelledEventAudit(cancelVisitDto: CancelVisitDto, visit: VisitDto): EventAuditDto = saveCancelledEventAudit(cancelVisitDto.actionedBy, cancelVisitDto.userType, cancelVisitDto.applicationMethodType, visit)

  fun saveCancelledMigratedEventAudit(cancelVisitDto: MigratedCancelVisitDto, visit: VisitDto): EventAuditDto = saveCancelledEventAudit(cancelVisitDto.actionedBy, STAFF, NOT_KNOWN, visit)

  fun saveMigratedVisitEventAudit(
    migrateVisitRequest: MigrateVisitRequestDto,
    visitEntity: Visit,
  ): EventAudit = saveAuditEvent(
    actionedBy = migrateVisitRequest.actionedBy,
    userType = STAFF,
    bookingReference = visitEntity.reference,
    applicationReference = visitEntity.getLastCompletedApplication()!!.reference,
    sessionTemplateReference = visitEntity.sessionSlot.sessionTemplateReference,
    eventAuditType = MIGRATED_VISIT,
    applicationMethodType = NOT_KNOWN,
    text = null,
  )

  fun updateCreateTimestamp(time: LocalDateTime, eventAudit: EventAudit): EventAuditDto {
    val eventAuditUpdated = eventAuditTransactionalService.updateCreateTimestamp(time, eventAudit)
    return EventAuditDto(eventAuditUpdated)
  }

  fun saveNotificationEventAudit(type: NotificationEventType, impactedVisit: VisitDto): EventAuditDto = EventAuditDto(
    saveAuditEvent(
      actionedBy = null,
      userType = SYSTEM,
      bookingReference = impactedVisit.reference,
      applicationReference = impactedVisit.applicationReference,
      sessionTemplateReference = impactedVisit.sessionTemplateReference,
      eventAuditType = EventAuditType.valueOf(type.name),
      applicationMethodType = NOT_KNOWN,
      text = null,
    ),
  )

  fun getLastEventForBooking(bookingReference: String): EventAuditDto? = eventAuditTransactionalService.getLastEventForBooking(bookingReference)?.let { EventAuditDto(it) }

  fun getLastEventForBookingOrMigration(bookingReference: String): EventAuditDto? = eventAuditTransactionalService.getLastEventForBookingOrMigration(bookingReference)?.let { EventAuditDto(it) }

  fun getBookedOrMigratedUser(bookingReference: String): ActionedBy? = eventAuditTransactionalService.getBookedOrMigratedUser(bookingReference)

  fun updateVisitApplicationAndSaveEvent(
    bookedVisitDto: VisitDto,
    bookingRequestDto: BookingRequestDto,
    eventType: EventAuditType,
    text: String?,
  ): EventAuditDto {
    if (bookedVisitDto.applicationReference == null) {
      // This should not happen as application reference should always be set here
      throw IllegalArgumentException("Visit application reference is not set")
    }
    try {
      eventAuditTransactionalService.updateVisitApplication(
        applicationReference = bookedVisitDto.applicationReference,
        bookingReference = bookedVisitDto.reference,
        applicationMethodType = bookingRequestDto.applicationMethodType,
      )
    } catch (_: InvocationTargetException) {
      LOG.error("Audit log does not exist for ${bookedVisitDto.applicationReference}")
    }

    return saveBookingEventAudit(
      bookingRequestDto.actionedBy,
      bookedVisitDto,
      eventType,
      bookingRequestDto.applicationMethodType,
      userType = bookingRequestDto.userType,
      text = text,
    )
  }

  fun findByBookingReferenceOrderById(bookingReference: String): List<EventAuditDto> = eventAuditTransactionalService.findByBookingReferenceOrderById(bookingReference).map { EventAuditDto(it) }

  private fun saveCancelledEventAudit(actionedByValue: String, userType: UserType, applicationMethodType: ApplicationMethodType, visit: VisitDto): EventAuditDto {
    val eventAuditType = if (visit.visitSubStatus == VisitSubStatus.WITHDRAWN) {
      REQUESTED_VISIT_WITHDRAWN
    } else {
      CANCELLED_VISIT
    }

    return EventAuditDto(
      saveAuditEvent(
        actionedBy = actionedByValue,
        userType = userType,
        bookingReference = visit.reference,
        applicationReference = visit.applicationReference,
        sessionTemplateReference = visit.sessionTemplateReference,
        eventAuditType = eventAuditType,
        applicationMethodType = applicationMethodType,
        text = null,
      ),
    )
  }

  fun savePairedVisitChangeEventAudit(
    visitEntity: Visit,
    type: EventAuditType,
    auditText: String,
  ): EventAudit = saveAuditEvent(
    actionedBy = null,
    userType = SYSTEM,
    bookingReference = visitEntity.reference,
    applicationReference = null,
    sessionTemplateReference = null,
    eventAuditType = type,
    applicationMethodType = NOT_APPLICABLE,
    text = auditText,
  )

  fun saveVisitRequestApprovedOrRejectedEventAudit(
    actionedByValue: String,
    visit: VisitDto,
    isApproved: Boolean,
    visitRequestRejectionReason: VisitRequestRejectionReason? = null,
  ): EventAuditDto {
    val eventType = if (isApproved) {
      EventAuditType.REQUESTED_VISIT_APPROVED
    } else {
      EventAuditType.REQUESTED_VISIT_REJECTED
    }

    return EventAuditDto(
      saveAuditEvent(
        actionedBy = actionedByValue,
        userType = STAFF,
        bookingReference = visit.reference,
        applicationReference = visit.applicationReference,
        sessionTemplateReference = visit.sessionTemplateReference,
        applicationMethodType = ApplicationMethodType.WEBSITE,
        text = if (isApproved) null else visitRequestRejectionReason?.name,
        eventAuditType = eventType,
      ),
    )
  }

  fun saveVisitRequestAutoRejectedEventAudit(visit: VisitDto, rejectionText: String): EventAuditDto = EventAuditDto(
    saveAuditEvent(
      actionedBy = null,
      userType = SYSTEM,
      bookingReference = visit.reference,
      applicationReference = visit.applicationReference,
      sessionTemplateReference = visit.sessionTemplateReference,
      EventAuditType.REQUESTED_VISIT_AUTO_REJECTED,
      applicationMethodType = NOT_APPLICABLE,
      text = rejectionText,
    ),
  )

  fun saveMergeEventAudits(visits: List<VisitDto>, oldPrisonerNumber: String, newPrisonerNumber: String) {
    val actionedBy = eventAuditTransactionalService.createOrGetActionBy(null, SYSTEM)
    val eventAudits = mutableListOf<EventAudit>()
    visits.forEach { visit ->
      eventAudits.add(
        EventAudit(
          actionedBy = actionedBy,
          bookingReference = visit.reference,
          applicationReference = visit.applicationReference,
          sessionTemplateReference = visit.sessionTemplateReference,
          type = EventAuditType.PRISONER_MERGED,
          applicationMethodType = NOT_APPLICABLE,
          text = "Prisoner merge event occurred - old prisoner number $oldPrisonerNumber, new prisoner number - $newPrisonerNumber",
        ),
      )
    }

    eventAuditTransactionalService.saveAllAuditEvents(eventAudits)
  }

  private fun saveAuditEvent(
    actionedBy: String?,
    userType: UserType,
    bookingReference: String?,
    applicationReference: String?,
    sessionTemplateReference: String?,
    eventAuditType: EventAuditType,
    applicationMethodType: ApplicationMethodType,
    text: String?,
  ): EventAudit = eventAuditTransactionalService.saveEventAudit(
    actionedByValue = actionedBy,
    userType = userType,
    bookingReference = bookingReference,
    applicationReference = applicationReference,
    sessionTemplateReference = sessionTemplateReference,
    eventAuditType = eventAuditType,
    applicationMethodType = applicationMethodType,
    text = text,
  )
}
