package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity

@Entity
@Table(
  name = "visit_notification_event_attribute",
)
class VisitNotificationEventAttribute(
  @Column(name = "visit_notification_event_id", unique = false, nullable = false)
  val visitNotificationEventId: Long,

  @Column(name = "attribute_name", nullable = false)
  @Enumerated(EnumType.STRING)
  val attributeName: NotificationEventAttributeType,

  @Column(name = "attribute_value", nullable = false)
  val attributeValue: String,

  @ManyToOne
  @JoinColumn(name = "visit_notification_event_id", updatable = false, insertable = false)
  val visitNotificationEvent: VisitNotificationEvent,
) : AbstractIdEntity()
