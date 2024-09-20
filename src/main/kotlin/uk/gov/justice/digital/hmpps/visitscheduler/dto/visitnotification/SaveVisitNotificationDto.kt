package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType

data class SaveVisitNotificationDto(
  val affectedVisits: List<VisitDto>,
  val type: NotificationEventType,
  val description: String? = null,
  val visitorId: Long? = null,
  val visitorRestrictionType: VisitorSupportedRestrictionType? = null,
)
