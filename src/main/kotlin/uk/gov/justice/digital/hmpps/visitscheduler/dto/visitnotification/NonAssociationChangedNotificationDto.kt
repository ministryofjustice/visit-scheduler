package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class NonAssociationChangedNotificationDto(
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val nonAssociationPrisonerNumber: String,
)
