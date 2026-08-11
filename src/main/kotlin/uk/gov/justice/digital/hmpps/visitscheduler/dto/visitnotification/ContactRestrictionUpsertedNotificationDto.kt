package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

data class ContactRestrictionUpsertedNotificationDto(
  val contactId: Long,

  val restrictionId: Long,
)
