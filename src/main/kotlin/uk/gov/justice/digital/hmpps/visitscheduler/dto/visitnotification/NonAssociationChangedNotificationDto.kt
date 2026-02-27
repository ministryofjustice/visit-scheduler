package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType

data class NonAssociationChangedNotificationDto(
  @field:NotNull
  val type: NonAssociationDomainEventType,
  @field:NotBlank
  val prisonerNumber: String,
  @field:NotBlank
  val nonAssociationPrisonerNumber: String,
)
