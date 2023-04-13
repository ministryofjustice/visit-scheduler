package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateLocationGroupDto(
  @JsonProperty("name")
  @Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @Schema(description = "list of locations for group", required = false)
  val locations: List<PermittedSessionLocationDto> = listOf(),
)
