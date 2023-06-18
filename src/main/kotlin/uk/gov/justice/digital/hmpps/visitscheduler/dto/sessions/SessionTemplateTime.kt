package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeValidation
import java.time.LocalTime

class SessionTemplateTime(
  @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  @field:SessionTimeValidation
  val startTime: LocalTime,

  @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  val endTime: LocalTime,
)
