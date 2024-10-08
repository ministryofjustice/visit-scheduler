package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PrisonerAlertCreatedUpdatedNotificationDto(
  @NotBlank
  val prisonerNumber: String,

  @NotBlank
  val description: String,

  @NotNull
  val alertsAdded: List<String>,

  @NotNull
  val alertsRemoved: List<String>,

  @NotNull
  val activeAlerts: List<String>,
)
