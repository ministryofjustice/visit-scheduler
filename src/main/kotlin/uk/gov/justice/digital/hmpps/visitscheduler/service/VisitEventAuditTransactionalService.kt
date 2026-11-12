package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PRISONER
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ActionedByRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import java.time.LocalDateTime

@Service
@Transactional
class VisitEventAuditTransactionalService(
  val actionedByRepository: ActionedByRepository,
  val eventAuditRepository: EventAuditRepository,
) {
  fun saveEventAudit(
    actionedByValue: String?,
    userType: UserType,
    bookingReference: String?,
    applicationReference: String?,
    sessionTemplateReference: String?,
    eventAuditType: EventAuditType,
    applicationMethodType: ApplicationMethodType,
    text: String?,
  ): EventAudit {
    val actionedBy = createOrGetActionBy(actionedByValue, userType)

    val eventAudit = eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = actionedBy,
        bookingReference = bookingReference,
        applicationReference = applicationReference,
        sessionTemplateReference = sessionTemplateReference,
        type = eventAuditType,
        applicationMethodType = applicationMethodType,
        text = text,
      ),
    )

    actionedBy.eventAuditList.add(eventAudit)
    actionedByRepository.saveAndFlush(actionedBy)
    return eventAudit
  }

  fun updateCreateTimestamp(time: LocalDateTime, eventAudit: EventAudit): EventAudit {
    eventAuditRepository.updateCreateTimestamp(time, eventAudit.id)
    return eventAuditRepository.getReferenceById(eventAudit.id)
  }

  @Transactional(readOnly = true)
  fun getLastEventForBooking(bookingReference: String): EventAudit? = eventAuditRepository.findLastBookedVisitEventByBookingReference(bookingReference)

  @Transactional(readOnly = true)
  fun getLastEventForBookingOrMigration(bookingReference: String): EventAudit? = eventAuditRepository.findLastBookedOrMigratedVisitEventByBookingReference(bookingReference)

  @Transactional(readOnly = true)
  fun getBookedOrMigratedUser(bookingReference: String): ActionedBy? = eventAuditRepository.getLastBookedOrMigratedUser(bookingReference)

  @Transactional(readOnly = true)
  fun findByBookingReferenceOrderById(bookingReference: String): List<EventAudit> = eventAuditRepository.findByBookingReferenceOrderById(bookingReference)

  fun updateVisitApplication(
    applicationReference: String,
    bookingReference: String,
    applicationMethodType: ApplicationMethodType,
  ) = eventAuditRepository.updateVisitApplication(applicationReference, bookingReference, applicationMethodType)

  fun createOrGetActionBy(actionedByValue: String? = null, userType: UserType): ActionedBy {
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

  fun saveAllAuditEvents(eventAudits: List<EventAudit>) {
    eventAuditRepository.saveAll(eventAudits)
  }
}
