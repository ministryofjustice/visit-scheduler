package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation

data class PermittedSessionLocationDto(

  @Schema(description = "Permitted Session Location id", example = "123", required = true)
  val id: Long,

  @Schema(description = "Level one location code", example = "c", required = true)
  var levelOneCode: String,

  @Schema(description = "Level two location code", example = "1", required = false)
  var levelTwoCode: String? = null,

  @Schema(description = "Level three location code", example = "001", required = false)
  var levelThreeCode: String? = null,

  @Schema(description = "Level four location code", example = "", required = false)
  var levelFourCode: String? = null,

) {
  constructor(permittedSessionLocationEntity: PermittedSessionLocation) : this(
    id = permittedSessionLocationEntity.id,
    levelOneCode = permittedSessionLocationEntity.levelOneCode,
    levelTwoCode = permittedSessionLocationEntity.levelTwoCode,
    levelThreeCode = permittedSessionLocationEntity.levelThreeCode,
    levelFourCode = permittedSessionLocationEntity.levelFourCode
  )
}
