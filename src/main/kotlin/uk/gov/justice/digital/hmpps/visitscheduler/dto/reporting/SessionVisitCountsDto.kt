package uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import java.time.LocalDate

data class SessionVisitCountsDto(
  @param:Schema(description = "Date of Report", example = "2023-09-01", required = true)
  @field:NotNull
  val reportDate: LocalDate,

  @param:Schema(description = "Prison code", example = "MDI", required = true)
  @field:NotBlank
  var prisonCode: String,

  @param:Schema(description = "If the prison had blocked the date for visits", example = "true", required = true)
  @field:NotNull
  var isBlockedDate: Boolean,

  @param:Schema(description = "False if no sessions on the date for prison, false otherwise", example = "true", required = true)
  @field:NotNull
  var hasSessionsOnDate: Boolean,

  @param:Schema(description = "Session Template reference", example = "aa-es-cc-qq", required = false)
  var sessionReference: String? = null,

  @param:Schema(description = "Start and end times for the session", required = false)
  var sessionTimeSlot: SessionTimeSlotDto? = null,

  @param:Schema(description = "Open and closed counts allowed for the session", required = false)
  var sessionCapacity: SessionCapacityDto? = null,

  @param:Schema(description = "visit type", example = "SOCIAL", required = false)
  var visitType: VisitType? = null,

  @param:Schema(description = "count of open visits booked on the session for that day", example = "1", required = false)
  var openBookedCount: Int? = 0,

  @param:Schema(description = "count of closed visits booked on the session for that day", example = "1", required = false)
  var closedBookedCount: Int? = 0,

  @param:Schema(description = "count of open visits cancelled on the session for that day", example = "0", required = false)
  var openCancelledCount: Int? = 0,

  @param:Schema(description = "count of closed visits cancelled on the session for that day", example = "1", required = false)
  var closedCancelledCount: Int? = 0,
)
