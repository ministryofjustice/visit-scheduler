package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotBlank

data class CourtVideoAppointmentNotificationDto(
  @field:NotBlank
  val appointmentInstanceId: String,
)
