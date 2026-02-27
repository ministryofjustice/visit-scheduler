package uk.gov.justice.digital.hmpps.visitscheduler.dto.audit

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import java.time.LocalDateTime

data class NotifyHistoryDto(
  @param:Schema(description = "The event audit id the notify event is associated with", required = true)
  val eventAuditId: Long,

  @param:Schema(description = "The notification id for Notify action", required = true)
  val notificationId: String,

  @param:Schema(description = "Notification Type (Email / SMS)", required = true)
  val notificationType: NotifyNotificationType,

  @param:Schema(description = "Notification Status", required = true)
  val status: NotifyStatus,

  @param:Schema(description = "The email or phone number the notification was sent to", required = false)
  val sentTo: String?,

  @param:Schema(description = "Notification Sent At", required = false)
  val sentAt: LocalDateTime? = null,

  @param:Schema(description = "Notification Completed At", required = false)
  val completedAt: LocalDateTime? = null,

  @param:Schema(description = "Notification Created At", required = false)
  val createdAt: LocalDateTime? = null,
) {
  constructor(visitNotifyHistory: VisitNotifyHistory) : this(
    eventAuditId = visitNotifyHistory.eventAuditId,
    notificationId = visitNotifyHistory.notificationId,
    notificationType = visitNotifyHistory.notificationType,
    status = visitNotifyHistory.status,
    sentTo = visitNotifyHistory.sentTo,
    sentAt = visitNotifyHistory.sentAt,
    completedAt = visitNotifyHistory.completedAt,
    createdAt = visitNotifyHistory.createdAt,
  )
}
