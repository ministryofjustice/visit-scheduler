package uk.gov.justice.digital.hmpps.visitscheduler.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import java.time.LocalDateTime

@Entity
@Table(name = "VISIT_NOTIFY_HISTORY")
class VisitNotifyHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  val id: Long = 0,

  @Column(name = "event_audit_id", nullable = false)
  var eventAuditId: Long,

  @Column(name = "notification_id", nullable = false)
  val notificationId: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false)
  val notificationType: NotifyNotificationType,

  @Column(name = "template_id", nullable = true)
  val templateId: String? = null,

  @Column(name = "template_version", nullable = true)
  val templateVersion: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  val status: NotifyStatus,

  @Column(name = "sent_at")
  val sentAt: LocalDateTime? = null,

  @Column(name = "completed_at")
  val completedAt: LocalDateTime? = null,

  @Column(name = "created_at")
  val createdAt: LocalDateTime? = null,

  @ManyToOne
  @JoinColumn(name = "event_audit_id", updatable = false, insertable = false)
  val eventAudit: EventAudit,
)
