package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.RESERVED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ActionedByRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class VisitEventAuditService {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Lazy
  @Autowired
  private lateinit var visitRepository: VisitRepository

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

    return EventAuditDto(eventAudit)
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
    )
  }

  fun saveCancelledEventAudit(cancelVisitDto: CancelVisitDto, visit: VisitDto): EventAuditDto {
    return saveCancelledEventAudit(cancelVisitDto.actionedBy, cancelVisitDto.applicationMethodType, visit)
  }

  fun saveCancelledMigratedEventAudit(cancelVisitDto: MigratedCancelVisitDto, visit: VisitDto): EventAuditDto {
    return saveCancelledEventAudit(cancelVisitDto.actionedBy, NOT_KNOWN, visit)
  }

  fun saveMigratedVisitEventAudit(
    migrateVisitRequest: MigrateVisitRequestDto,
    visitEntity: Visit,
  ): EventAuditDto {
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

    migrateVisitRequest.createDateTime?.let {
      visitRepository.updateCreateTimestamp(it, visitEntity.id)
      if (migrateVisitRequest.visitStatus == BOOKED) {
        eventAuditRepository.updateCreateTimestamp(it, eventAudit.id)
      }
    }

    // Do this at end of this method, otherwise modify date would be overridden
    migrateVisitRequest.modifyDateTime?.let {
      visitRepository.updateModifyTimestamp(it, visitEntity.id)
      if (migrateVisitRequest.visitStatus == CANCELLED) {
        eventAuditRepository.updateCreateTimestamp(it, eventAudit.id)
      }
    }

    return EventAuditDto(eventAudit)
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
    )
  }

  @Transactional(readOnly = true)
  fun getLastEventForBooking(bookingReference: String): EventAuditDto? {
    return eventAuditRepository.findLastBookedVisitEventByBookingReference(bookingReference)?.let {
      EventAuditDto(it)
    }
  }

  @Transactional(readOnly = true)
  fun getLastUserToUpdateSlotByReference(bookingReference: String): String {
    val eventAuditType = eventAuditRepository.getLastUserToUpdateBookingByReference(bookingReference)

    return when (eventAuditType.userType) {
      STAFF -> eventAuditType.userName!!
      PUBLIC -> eventAuditType.bookerReference!!
      SYSTEM -> ""
    }
  }

  private fun saveCancelledEventAudit(actionedByValue: String, applicationMethodType: ApplicationMethodType, visit: VisitDto): EventAuditDto {
    val actionedBy = createOrGetActionBy(actionedByValue, STAFF)

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
    )
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
