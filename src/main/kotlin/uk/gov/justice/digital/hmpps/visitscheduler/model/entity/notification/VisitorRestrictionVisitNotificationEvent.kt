package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.base.AbstractIdEntity
import uk.gov.justice.digital.hmpps.visitscheduler.utils.QuotableEncoder
import java.time.LocalDateTime

@Entity
@DiscriminatorValue(value = "Prisoner-alerts-updated")
class VisitorRestrictionVisitNotificationEvent(
  id: Long = -1L,
  bookingReference: String,
  description: String?,

  @Column(nullable = true)
  var nomisPersonId: Long,

  @Column(nullable = true)
  @Enumerated(EnumType.STRING)
  var restrictionType: VisitorSupportedRestrictionType,

) : VisitNotificationEvent(
  bookingReference = bookingReference,
  type = NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT,
  description = description
)
