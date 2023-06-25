package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionCapacityValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionDateRangeValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.SessionTimeSlotValidation
import java.time.DayOfWeek

data class CreateSessionTemplateDto(

  @Schema(description = "Name for Session template", example = "Monday Xmas", required = true)
  @field:NotBlank
  @field:Size(max = 100)
  val name: String,

  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(description = "The start and end time of the generated visit session(s)", required = true)
  @field:SessionTimeSlotValidation
  val sessionTimeSlot: SessionTimeSlotDto,

  @Schema(description = "The start and end date of the Validity period for the session template", required = true)
  @field:SessionDateRangeValidation
  val sessionDateRange: SessionDateRangeDto,

  @Schema(description = "Visit Room", example = "Visits Main Hall", required = true)
  @field:NotBlank
  @field:Size(max = 255)
  val visitRoom: String,

  @Schema(description = "The open and closed capacity of the session template", required = true)
  @field:SessionCapacityValidation
  @field:Valid
  val sessionCapacity: SessionCapacityDto,

  @Schema(description = "day of week fpr visit", example = "MONDAY", required = true)
  val dayOfWeek: DayOfWeek,


  @Schema(description = "biWeekly time table", example = "true", required = true)
  val biWeekly: Boolean,

  @Schema(description = "is session template active", example = "true", required = true)
  val active: Boolean,

  @Schema(description = "list of group references for permitted session location groups", required = false)
  val locationGroupReferences: List<String>? = listOf(),

  @Schema(description = "list of group references for allowed prisoner category groups", required = false)
  val categoryGroupReferences: List<String>? = listOf(),

  @Schema(description = "list of group references for allowed prisoner incentive levels", required = false)
  val incentiveLevelGroupReferences: List<String>? = listOf(),
)
