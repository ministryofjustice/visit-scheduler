package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalTime

data class UpdateSessionTemplateDto(

  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  val name: String? = null,

  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  val startTime: LocalTime? = null,

  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  val endTime: LocalTime? = null,

  @Schema(description = "The start of the Validity period for the session template", example = "2019-12-02", required = true)
  @field:NotNull
  val validFromDate: LocalDate? = null,

  @Schema(description = "The end of the Validity period for the session template", example = "2019-12-02", required = false)
  val validToDate: LocalDate? = null,

  @Schema(description = "closed capacity", example = "10", required = true)
  val closedCapacity: Int? = null,

  @Schema(description = "open capacity", example = "50", required = true)
  val openCapacity: Int? = null,

  @Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = null,

  @Schema(description = "enhanced privilege", example = "true", required = true)
  val enhanced: Boolean? = null,

  @Schema(description = "biWeekly time table", example = "true", required = true)
  val biWeekly: Boolean? = null,

  @Schema(description = "list of included prisoner categories", required = false)
  val includedPrisonerCategories: List<String>? = null,

  @Schema(description = "list of excluded prisoner categories", required = false)
  val excludedPrisonerCategories: List<String>? = null,
)
