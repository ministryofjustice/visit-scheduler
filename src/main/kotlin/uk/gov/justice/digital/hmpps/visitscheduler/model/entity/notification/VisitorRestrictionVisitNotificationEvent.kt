package uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType

@Entity
@DiscriminatorValue(value = "Prisoner-alerts-updated")
class VisitorRestrictionVisitNotificationEvent(
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
