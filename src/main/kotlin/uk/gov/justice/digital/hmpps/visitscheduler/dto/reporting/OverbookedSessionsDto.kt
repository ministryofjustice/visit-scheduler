package uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import java.time.LocalDate

data class OverbookedSessionsDto(
  @Schema(description = "Date of Visit ", example = "2023-09-01", required = true)
  @field:NotNull
  val sessionDate: LocalDate,

  @Schema(description = "Prison code", example = "MDI", required = true)
  @field:NotBlank
  var prisonCode: String,

  @Schema(description = "Start and end times for the session", required = true)
  var sessionTimeSlot: SessionTimeSlotDto,

  @Schema(description = "Open and closed counts allowed for the session", required = true)
  var sessionCapacity: SessionCapacityDto,

  @Schema(description = "count of open visits booked on the session for that day", example = "1", required = true)
  var openCount: Int,

  @Schema(description = "count of closed visits booked on the session for that day", example = "1", required = true)
  var closedCount: Int,
)
