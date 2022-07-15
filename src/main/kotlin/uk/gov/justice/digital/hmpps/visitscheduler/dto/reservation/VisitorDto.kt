package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visitor
import javax.validation.constraints.NotNull

@Schema(description = "Visitor")
data class VisitorDto(
  @Schema(description = "Person ID (nomis) of the visitor", example = "1234556", required = true)
  @field:NotNull
  val nomisPersonId: Long,
) {

  constructor(entity: Visitor) : this(
    nomisPersonId = entity.nomisPersonId,
  )
}
