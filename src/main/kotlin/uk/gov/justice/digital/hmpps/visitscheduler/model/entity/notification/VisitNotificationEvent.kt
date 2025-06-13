package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.CascadeType.ALL
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@Table(
  name = "visit_notification_event",
)
class VisitNotificationEvent(
  @Column(name = "booking_reference", unique = false, nullable = false)
  var bookingReference: String,

  @Column(name = "VISIT_ID", nullable = false)
  var visitId: Long,

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  val type: NotificationEventType,

  @ManyToOne
  @JoinColumn(name = "VISIT_ID", updatable = false, insertable = false, nullable = false)
  val visit: Visit,

  @Transient
  private val _reference: String = "",
) : AbstractIdEntity() {
  @CreationTimestamp
  @Column
  val createTimestamp: LocalDateTime = LocalDateTime.now()

  @Column(nullable = false)
  var reference = _reference

  @OneToMany(fetch = FetchType.EAGER, cascade = [ALL], mappedBy = "visitNotificationEvent", orphanRemoval = true)
  val visitNotificationEventAttributes: MutableList<VisitNotificationEventAttribute> = mutableListOf()

  @PostPersist
  fun createReference() {
    if (_reference.isBlank()) {
      reference = QuotableEncoder(delimiter = "*", minLength = 8).encode(id)
    }
  }
}
