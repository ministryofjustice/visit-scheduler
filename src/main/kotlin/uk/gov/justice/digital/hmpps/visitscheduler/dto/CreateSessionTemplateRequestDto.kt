package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateSessionTemplateRequestDto(
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  @field:NotNull
  val startTime: LocalTime,
  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  @field:NotNull
  val endTime: LocalTime,
  @Schema(description = "The start of the Validity period for the session template", example = "2019-12-02", required = true)
  @field:NotNull
  val validFromDate: LocalDate,
  @Schema(description = "The end of the Validity period for the session template", example = "2019-12-02", required = false)
  val validToDate: LocalDate? = null,
  @Schema(description = "visit type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "visit room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "closed capacity", example = "10", required = true)
  @field:NotNull
  val closedCapacity: Int,
  @Schema(description = "open capacity", example = "50", required = true)
  @field:NotNull
  val openCapacity: Int,
  @Schema(description = "day of week for visit", example = "MONDAY", required = true)
  val dayOfWeek: DayOfWeek
)
