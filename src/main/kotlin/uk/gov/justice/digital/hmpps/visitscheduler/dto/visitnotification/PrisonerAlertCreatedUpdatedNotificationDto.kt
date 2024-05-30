package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import jakarta.validation.constraints.NotBlank

data class PrisonerAlertCreatedUpdatedNotificationDto(
  @NotBlank
  val prisonerNumber: String,

  @JsonInclude(Include.NON_NULL)
  val alertsAdded: List<String>? = emptyList(),
)
