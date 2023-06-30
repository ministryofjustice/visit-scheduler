package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import java.time.LocalDate

@Schema(description = "Prison dto")
data class PrisonDto(

  @Schema(description = "prison code", example = "BHI", required = true)
  var code: String,

  @Schema(description = "is prison active", example = "true", required = true)
  var active: Boolean = false,

  @Schema(description = "exclude dates", required = false)
  var excludeDates: Set<LocalDate> = mutableSetOf(),
) {

  constructor(prisonEntity: Prison) : this(
    code = prisonEntity.code,
    active = prisonEntity.active,
    excludeDates = prisonEntity.excludeDates.map { it.excludeDate }.toSet(),
  )
}
