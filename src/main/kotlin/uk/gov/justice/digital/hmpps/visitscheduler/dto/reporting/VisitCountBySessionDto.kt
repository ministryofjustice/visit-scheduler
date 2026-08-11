package uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto

data class VisitCountBySessionDto(
  @param:Schema(description = "Session Template reference", example = "aa-es-cc-qq", required = false)
  var sessionReference: String?,

  @param:Schema(description = "Start and end times for the session", required = true)
  var sessionTimeSlot: SessionTimeSlotDto,

  @param:Schema(description = "Open and closed counts allowed for the session", required = true)
  var sessionCapacity: SessionCapacityDto,

  @param:Schema(description = "visit type", example = "SOCIAL", required = true)
  var visitType: VisitType,

  @param:Schema(description = "count of open visits booked on the session for that day", example = "1", required = true)
  var openBookedCount: Int,

  @param:Schema(description = "count of visitors on OPEN visits", example = "15", required = true)
  var openBookedVisitorsCount: Int,

  @param:Schema(description = "count of closed visits booked on the session for that day", example = "1", required = true)
  var closedBookedCount: Int,

  @param:Schema(description = "count of visitors on CLOSED visits", example = "3", required = true)
  var closedBookedVisitorsCount: Int,

  @param:Schema(description = "count of open visits cancelled on the session for that day", example = "0", required = true)
  var openCancelledCount: Int,

  @param:Schema(description = "count of closed visits cancelled on the session for that day", example = "1", required = true)
  var closedCancelledCount: Int,

  @param:Schema(description = "Visit Room name, if available", example = "Visit Room 1", required = true)
  var visitRoom: String,
)
