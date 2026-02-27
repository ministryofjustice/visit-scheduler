package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup

data class SessionLocationGroupDto(
  @param:JsonProperty("name")
  @param:Schema(description = "Group name", example = "Main group", required = true)
  @field:NotBlank
  val name: String,

  @param:Schema(description = "Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,

  @param:Schema(description = "list of locations for group", required = false)
  val locations: List<PermittedSessionLocationDto> = listOf(),
) {
  constructor(sessionLocationGroup: SessionLocationGroup) : this(
    name = sessionLocationGroup.name,
    reference = sessionLocationGroup.reference,
    locations = sessionLocationGroup.sessionLocations.map { PermittedSessionLocationDto(it) },
  )
}
