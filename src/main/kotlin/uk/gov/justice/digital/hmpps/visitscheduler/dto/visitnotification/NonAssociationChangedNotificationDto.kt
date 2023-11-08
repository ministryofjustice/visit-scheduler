package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType

data class NonAssociationChangedNotificationDto(
  @NotNull
  val type: NonAssociationDomainEventType,
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val nonAssociationPrisonerNumber: String,
)
