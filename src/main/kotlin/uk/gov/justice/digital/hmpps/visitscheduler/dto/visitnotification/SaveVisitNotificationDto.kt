package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType

data class SaveVisitNotificationDto(
  val affectedVisits: List<VisitDto>,
  val type: NotificationEventType,
  val notificationEventAttributes: HashMap<NotificationEventAttributeType, String>?,
)
