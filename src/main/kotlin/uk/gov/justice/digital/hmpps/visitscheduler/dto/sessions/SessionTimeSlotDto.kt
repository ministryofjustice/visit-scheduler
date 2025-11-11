package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeSlotValidation
import java.time.LocalTime

data class SessionTimeSlotDto(
  @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
  @param:Schema(description = "The start time of the generated visit session(s)", example = "10:30", required = true)
  @field:SessionTimeSlotValidation
  val startTime: LocalTime,

  @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
  @param:Schema(description = "The end time of the generated visit session(s)", example = "11:30", required = true)
  val endTime: LocalTime,
)
