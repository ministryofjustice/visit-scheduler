package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.validation.NullableNotBlank

data class PermittedSessionLocationDto(

  @Schema(description = "Level one location code", example = "w", required = true)
  @NotNull
  @NullableNotBlank
  var levelOneCode: String,

  @Schema(description = "Level two location code", example = "c", required = false)
  @NullableNotBlank
  var levelTwoCode: String? = null,

  @Schema(description = "Level three location code", example = "1", required = false)
  @NullableNotBlank
  var levelThreeCode: String? = null,

  @Schema(description = "Level four location code", example = "001", required = false)
  @NullableNotBlank
  var levelFourCode: String? = null,
) {
  constructor(permittedSessionLocationEntity: PermittedSessionLocation) : this(
    levelOneCode = permittedSessionLocationEntity.levelOneCode,
    levelTwoCode = permittedSessionLocationEntity.levelTwoCode,
    levelThreeCode = permittedSessionLocationEntity.levelThreeCode,
    levelFourCode = permittedSessionLocationEntity.levelFourCode,
  )
}
