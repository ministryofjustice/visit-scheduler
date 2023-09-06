package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import java.time.LocalDateTime

@Entity
@Table(
  name = "visit_notification_event",
)
class VisitNotificationEvent(

  @Column(name = "VISIT_ID", unique = false, nullable = false)
  var visitId: Long,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: NotificationEventType,

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now(),

) : AbstractIdEntity()
