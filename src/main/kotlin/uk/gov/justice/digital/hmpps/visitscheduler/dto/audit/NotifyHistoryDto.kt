package uk.gov.justice.digital.hmpps.visitscheduler.dto.audit

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import java.time.LocalDateTime

data class NotifyHistoryDto(

  @Schema(description = "The notification id for Notify action", required = true)
  val notificationId: String,

  @Schema(description = "Notification Type (Email / SMS)", required = true)
  val notificationType: NotifyNotificationType,

  @Schema(description = "Notification Status", required = true)
  val status: NotifyStatus,

  @Schema(description = "Notification Sent At", required = false)
  val sentAt: LocalDateTime? = null,

  @Schema(description = "Notification Completed At", required = false)
  val completedAt: LocalDateTime? = null,

  @Schema(description = "Notification Created At", required = false)
  val createdAt: LocalDateTime? = null,
) {
  constructor(visitNotifyHistory: VisitNotifyHistory) : this(
    notificationId = visitNotifyHistory.notificationId,
    notificationType = visitNotifyHistory.notificationType,
    status = visitNotifyHistory.status,
    sentAt = visitNotifyHistory.sentAt,
    completedAt = visitNotifyHistory.completedAt,
    createdAt = visitNotifyHistory.createdAt,
  )
}
