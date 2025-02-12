package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus.SENDING
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCallbackNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCreateNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotifyHistoryRepository

@Service
class VisitNotifyService(
  private val visitNotifyHistoryRepository: VisitNotifyHistoryRepository,
  private val eventAuditRepository: EventAuditRepository,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleCreateNotifyEvent(notifyCreateNotification: NotifyCreateNotificationDto) {
    logger.debug("handleCreateNotifyEvent called with parameters - {}", notifyCreateNotification)
    val eventAudit = getEventAudit(notifyCreateNotification.eventAuditReference)
    val isExistingNotification = visitNotifyHistoryRepository.doesNotificationExist(notifyCreateNotification.notificationId, eventAudit.id)

    if (!isExistingNotification) {
      val visitNotifyEventEntity = getVisitNotifyHistory(eventAudit, notifyCreateNotification)
      visitNotifyHistoryRepository.save(visitNotifyEventEntity)
    } else {
      logger.info("visit notification not created as callback already called for notification ID - {}", notifyCreateNotification.notificationId)
    }

    logger.debug("handleCreateNotifyEvent call with parameters - {} completed.", notifyCreateNotification)
  }

  @Transactional
  fun handleCallbackNotifyEvent(notifyCallbackNotification: NotifyCallbackNotificationDto) {
    logger.debug("handleCallbackNotifyEvent called with parameters - {}", notifyCallbackNotification)
    val eventAudit = getEventAudit(notifyCallbackNotification.eventAuditReference)
    val isExistingNotification = visitNotifyHistoryRepository.doesNotificationExist(notifyCallbackNotification.notificationId, eventAudit.id)

    if (!isExistingNotification) {
      val visitNotifyEventEntity = getVisitNotifyHistory(eventAudit, notifyCallbackNotification)
      visitNotifyHistoryRepository.saveAndFlush(visitNotifyEventEntity)
    } else {
      visitNotifyHistoryRepository.updateNotification(
        notificationId = notifyCallbackNotification.notificationId,
        notificationType = NotifyNotificationType.get(notifyCallbackNotification.notificationType),
        templateId = notifyCallbackNotification.templateId,
        templateVersion = notifyCallbackNotification.templateVersion,
        status = NotifyStatus.get(notifyCallbackNotification.status),
        sentTo = notifyCallbackNotification.sentTo,
        sentAt = notifyCallbackNotification.sentAt,
        completedAt = notifyCallbackNotification.completedAt,
        createdAt = notifyCallbackNotification.createdAt,
      )
    }

    logger.debug("handleCallbackNotifyEvent call with parameters - {} completed.", notifyCallbackNotification)
  }

  private fun getVisitNotifyHistory(eventAudit: EventAudit, notifyCreateNotification: NotifyCreateNotificationDto): VisitNotifyHistory = VisitNotifyHistory(
    eventAuditId = notifyCreateNotification.eventAuditReference.toLong(),
    notificationType = NotifyNotificationType.get(notifyCreateNotification.notificationType),
    status = SENDING,
    createdAt = notifyCreateNotification.createdAt,
    notificationId = notifyCreateNotification.notificationId,
    templateId = notifyCreateNotification.templateId,
    templateVersion = notifyCreateNotification.templateVersion,
    eventAudit = eventAudit,
  )

  private fun getVisitNotifyHistory(eventAudit: EventAudit, notifyCallbackNotification: NotifyCallbackNotificationDto): VisitNotifyHistory = VisitNotifyHistory(
    eventAuditId = notifyCallbackNotification.eventAuditReference.toLong(),
    notificationType = NotifyNotificationType.get(notifyCallbackNotification.notificationType),
    status = NotifyStatus.get(notifyCallbackNotification.status),
    notificationId = notifyCallbackNotification.notificationId,
    templateId = notifyCallbackNotification.templateId,
    templateVersion = notifyCallbackNotification.templateVersion,
    sentTo = notifyCallbackNotification.sentTo,
    createdAt = notifyCallbackNotification.createdAt,
    completedAt = notifyCallbackNotification.completedAt,
    sentAt = notifyCallbackNotification.sentAt,
    eventAudit = eventAudit,
  )

  private fun getEventAudit(eventAuditReference: String): EventAudit {
    val eventAuditId = eventAuditReference.toLong()
    val eventAudit = eventAuditRepository.findById(eventAuditId)
    if (eventAudit.isPresent) {
      return eventAudit.get()
    } else {
      throw ValidationException("Associated Event Audit entry with ID - $eventAuditReference not found")
    }
  }
}
