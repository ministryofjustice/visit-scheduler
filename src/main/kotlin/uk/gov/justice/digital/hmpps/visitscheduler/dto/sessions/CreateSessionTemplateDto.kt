package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Shape
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class CreateSessionTemplateDto(

  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  val name: String,

  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  val startTime: LocalTime,
  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  val endTime: LocalTime,

  @Schema(description = "The start of the Validity period for the session template", example = "2019-12-02", required = true)
  @field:NotNull
  val validFromDate: LocalDate,

  @Schema(description = "The end of the Validity period for the session template", example = "2019-12-02", required = false)
  val validToDate: LocalDate? = null,

  @Schema(description = "visit room", example = "A1", required = true)
  val visitRoom: String,

  @Schema(description = "closed capacity", example = "10", required = true)
  val closedCapacity: Int,

  @Schema(description = "open capacity", example = "50", required = true)
  val openCapacity: Int,

  @Schema(description = "day of week fpr visit", example = "MONDAY", required = true)
  val dayOfWeek: DayOfWeek,

  @Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = listOf(),

  @Schema(description = "enhanced privilege", example = "true", required = true)
  val enhanced: Boolean,

  @Schema(description = "biWeekly time table", example = "true", required = true)
  val biWeekly: Boolean,

  @Schema(description = "list of group references for allowed prisoner category groups", required = false)
  val categoryGroupReferences: List<String>? = listOf(),
)
