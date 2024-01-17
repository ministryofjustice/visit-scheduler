package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

class PrisonerVisitsNotificationDto(
  @Schema(description = "Prisoner Number", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerNumber: String,
  @Schema(description = "Booked by user name ", example = "SMITH1", required = true)
  @field:NotBlank
  val bookedByUserName: String,
  @Schema(description = "The date of the visit", example = "2023-11-08", required = true)
  @field:NotBlank
  val visitDate: LocalDate,
  @Schema(description = "OldVisit Booking Reference", example = "v9-d7-ed-7u", required = true)
  val bookingReference: String,
)
