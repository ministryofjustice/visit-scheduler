package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import java.time.LocalDateTime

@Repository
interface VisitNotifyHistoryRepository : JpaRepository<VisitNotifyHistory, Long> {
  @Query(
    "SELECT count(vnh) > 0 FROM VisitNotifyHistory vnh WHERE vnh.notificationId = :notificationId and vnh.eventAuditId = :eventAuditId",
  )
  fun doesNotificationExist(notificationId: String, eventAuditId: Long): Boolean

  @Transactional
  @Modifying
  @Query(
    "update VisitNotifyHistory vnh set " +
      "vnh.notificationType = :notificationType," +
      "vnh.templateId = :templateId," +
      "vnh.templateVersion = :templateVersion," +
      "vnh.status = :status," +
      "vnh.sentTo = :sentTo," +
      "vnh.sentAt = :sentAt," +
      "vnh.completedAt = :completedAt," +
      "vnh.createdAt = :createdAt " +
      "WHERE vnh.notificationId = :notificationId",
  )
  fun updateNotification(
    notificationId: String,
    notificationType: NotifyNotificationType,
    templateId: String,
    templateVersion: String,
    status: NotifyStatus,
    sentTo: String? = null,
    sentAt: LocalDateTime? = null,
    completedAt: LocalDateTime? = null,
    createdAt: LocalDateTime,
  ): Int
}
