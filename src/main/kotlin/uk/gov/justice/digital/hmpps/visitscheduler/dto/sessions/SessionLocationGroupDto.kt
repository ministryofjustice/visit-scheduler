package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import javax.validation.constraints.NotBlank

data class SessionLocationGroupDto(

  @JsonProperty("name")
  @Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @Schema(description = "Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,

  @Schema(description = "list of locations for group", required = false)
  val locations: List<PermittedSessionLocationDto> = listOf()

) {
  constructor(sessionLocationGroup: SessionLocationGroup) : this(
    name = sessionLocationGroup.name,
    reference = sessionLocationGroup.reference,
    locations = sessionLocationGroup.sessionLocations.map { PermittedSessionLocationDto(it) },
  )
}
