package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionCapacityValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionDateRangeValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeSlotValidation

data class UpdateSessionTemplateDto(
  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  @field:Size(max = 100)
  val name: String? = null,

  @Schema(description = "The start and end time of the generated visit session(s)", required = false)
  @field:SessionTimeSlotValidation
  val sessionTimeSlot: SessionTimeSlotDto?,

  @Schema(description = "The start and end date of the Validity period for the session template", required = false)
  @field:SessionDateRangeValidation
  val sessionDateRange: SessionDateRangeDto?,

  @Schema(description = "Visit Room", example = "Visits Main Hall", required = false)
  @field:Size(max = 255)
  val visitRoom: String?,

  @Schema(description = "The open and closed capacity of the session template", required = false)
  @field:SessionCapacityValidation
  @field:Valid
  val sessionCapacity: SessionCapacityDto?,

  @Schema(description = "number of weeks until the weekly day is repeated", example = "1", required = false)
  @field:Min(1)
  val weeklyFrequency: Int?,

  @Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = listOf(),

  @Schema(description = "list of group references for allowed prisoner category groups", required = false)
  val categoryGroupReferences: List<String>? = listOf(),

  @Schema(description = "list of group references for allowed prisoner incentive levels", required = false)
  val incentiveLevelGroupReferences: List<String>? = listOf(),
)
