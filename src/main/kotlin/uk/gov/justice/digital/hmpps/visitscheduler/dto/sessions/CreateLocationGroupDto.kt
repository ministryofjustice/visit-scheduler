package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotBlank

data class CreateLocationGroupDto(

  @Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(description = "list of locations for group", required = false)
  val locations: List<PermittedSessionLocationDto> = listOf()

)
