package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class PrisonerAlertNotificationDto(
  @field:NotBlank
  val prisonerNumber: String,

  @field:NotBlank
  val alertCode: String,

  @field:NotBlank
  val alertUuid: String,

  @field:NotBlank
  val description: String,
)
