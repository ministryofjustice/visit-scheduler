package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@Table(name = "visit_notification_event")
@Inheritance(strategy= InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
open class VisitNotificationEvent(

  @Column(name = "booking_reference", unique = false, nullable = false)
  var bookingReference: String,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: NotificationEventType,

  @Column(nullable = true)
  val description: String? = null,

  @Transient
  private val _reference: String = "",

) : AbstractIdEntity() {

  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now()

  @Column(nullable = false)
  var reference = _reference

  @PostPersist
  fun createReference() {
    if (_reference.isBlank()) {
      reference = QuotableEncoder(delimiter = "*", minLength = 8).encode(id)
    }
  }
}
