package uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerHousingLevelDto(
  @Schema(description = "The level (starting from 1) of the individual location. The highest number level will be the cell.", example = "1", required = true)
  val level: Int,

  @Schema(description = "The code for the location e.g. 010 for a cell, A for a wing", example = "010", required = true)
  val code: String,

  @Schema(description = "The type of the location - from LIVING_UNIT reference code", example = "WING", required = false)
  val type: String? = null,

  @Schema(description = "Description of the location, either from the user description if set or reference code description and code", example = "Wing A", required = true)
  val description: String
)
