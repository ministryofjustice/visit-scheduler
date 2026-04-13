package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class ContactRestrictionUpsertedNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,

  val contactId: Long,

  val prisonerContactId: Long,

  val restrictionId: Long,
)
