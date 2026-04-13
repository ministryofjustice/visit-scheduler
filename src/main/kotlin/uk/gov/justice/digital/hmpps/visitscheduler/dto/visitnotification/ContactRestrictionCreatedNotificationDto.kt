package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

data class ContactRestrictionCreatedNotificationDto(
  val visitorId: Long,

  val restrictionId: Long,
)
