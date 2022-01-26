package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor

@Schema(description = "Visit")
data class VisitorDto(
  @Schema(description = "Visit id", example = "123", required = true) val visitId: Long,
  @Schema(description = "person ID (nomis) of the visitor", example = "1234", required = true) val nomisPersonId: Long,
  @Schema(description = "indicates lead visitor for this visit", example = "true", required = true) val leadVisitor: Boolean,
) {

  constructor(visitVisitorEntity: VisitVisitor) : this(
    visitId = visitVisitorEntity.id.visitId,
    nomisPersonId = visitVisitorEntity.id.nomisPersonId,
    leadVisitor = visitVisitorEntity.leadVisitor,
  )
}
