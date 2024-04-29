package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel

data class CreateIncentiveGroupDto(
  @Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(description = "list of allowed incentive levels for group", required = false)
  val incentiveLevels: List<IncentiveLevel> = listOf(),
)
