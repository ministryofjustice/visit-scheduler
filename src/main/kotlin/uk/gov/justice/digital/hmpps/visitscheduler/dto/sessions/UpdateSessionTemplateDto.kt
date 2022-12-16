package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class UpdateSessionTemplateDto(

  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  val name: String,

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

  @Schema(description = "list of references for permitted session location groups", required = false)
  val referencesForPermittedLocationGroups: List<String>? = listOf()
)
