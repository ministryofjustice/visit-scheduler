package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import javax.validation.constraints.NotNull

@Schema(description = "Visitor")
data class VisitorDto(
  @Schema(description = "Person ID (nomis) of the visitor", example = "1234", required = true)
  @field:NotNull
  val nomisPersonId: Long,
  @Schema(description = "true if visitor is the contact for the visit otherwise false", example = "true", required = false)
  val visitContact: Boolean?
) {

  constructor(visitVisitorEntity: VisitVisitor) : this(
    nomisPersonId = visitVisitorEntity.nomisPersonId,
    visitContact = visitVisitorEntity.visitContact,
  )
}
