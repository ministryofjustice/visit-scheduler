package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class NonAssociationChangedNotificationDto(
  @NotBlank
  val prisonerNumber: String,
  @NotBlank
  val nonAssociationPrisonerNumber: String,
  @NotNull
  val validFromDate: LocalDate,
  val validToDate: LocalDate? = null,
)
