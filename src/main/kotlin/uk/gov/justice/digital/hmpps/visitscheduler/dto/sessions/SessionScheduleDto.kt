package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Shape
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionTemplateFrequency
import java.time.LocalDate
import java.time.LocalTime

@Schema(description = "Session schedule")
data class SessionScheduleDto(

  @Schema(description = "Session Template Reference", example = "v9d.7ed.7u", required = true)
  val sessionTemplateReference: String,

  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  @Schema(description = "The start time for this visit session", example = "12:00", required = true)
  val startTime: LocalTime,

  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  @Schema(description = "The end timestamp for this visit session", example = "14:30", required = true)
  val endTime: LocalTime,

  @Schema(
    description = "The capacity for the session",
    required = true,
  )
  val capacity: SessionCapacityDto,

  @Schema(description = "prisoner location groups", example = "Wing C", required = false)
  val prisonerLocationGroupNames: List<String>,

  @Schema(description = "prisoner category groups", example = "Category A Prisoners", required = false)
  val prisonerCategoryGroupNames: List<String>,

  @Schema(description = "The session template frequency", example = "BI_WEEKLY", required = true)
  val sessionTemplateFrequency: SessionTemplateFrequency,

  @Schema(description = "The end date of sessionTemplate", example = "2020-11-01", required = false)
  val sessionTemplateEndDate: LocalDate?,

)
