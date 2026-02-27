package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PrisonerAlertCreatedUpdatedNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,

  @field:NotBlank
  val description: String,

  @field:NotNull
  val alertsAdded: List<String>,

  @field:NotNull
  val alertsRemoved: List<String>,

  @field:NotNull
  val activeAlerts: List<String>,
)
