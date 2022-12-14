package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateSessionTemplateDto(

  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  val startTime: LocalTime,
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

  @Schema(description = "day of week fpr visit", example = "MONDAY", required = false)
  val dayOfWeek: DayOfWeek?,

  @Schema(description = "list of references for permitted session location groups", example = "af-ed-cb-fc", required = false)
  val referencesForPermittedLocationGroups: List<String>? = listOf()
)
