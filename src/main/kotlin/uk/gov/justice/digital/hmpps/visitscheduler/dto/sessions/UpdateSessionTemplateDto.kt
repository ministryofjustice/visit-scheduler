package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionCapacityValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionValidDateValidation

data class UpdateSessionTemplateDto(
  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  @field:Size(max = 100)
  val name: String? = null,

  @JsonFormat(pattern = "HH:mm", shape = JsonFormat.Shape.STRING)
  @Schema(description = "The start and end time of the generated visit session(s)", required = false)
  @field:SessionTimeValidation
  val sessionTemplateTime: SessionTemplateTime?,

  @Schema(description = "The start and end date of the Validity period for the session template", required = false)
  @field:SessionValidDateValidation
  val sessionTemplateValidDate: SessionTemplateValidDate?,

  @Schema(description = "The open and closed capacity of the session template", required = false)
  @field:SessionCapacityValidation
  @field:Valid
  val sessionTemplateCapacity: SessionTemplateCapacity?,

  @Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = null,

  @Schema(description = "biWeekly time table", example = "true", required = false)
  val biWeekly: Boolean? = null,

  @Schema(description = "list of group references for allowed prisoner category groups", required = false)
  val categoryGroupReferences: List<String>? = listOf(),

  @Schema(description = "list of group references for allowed prisoner incentive levels", required = false)
  val incentiveLevelGroupReferences: List<String>? = listOf(),
)
