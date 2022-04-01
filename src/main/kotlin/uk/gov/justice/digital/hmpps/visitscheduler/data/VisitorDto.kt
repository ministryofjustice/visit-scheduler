package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor

@Schema(description = "Visitor")
data class VisitorDto(
  @Schema(description = "person ID (nomis) of the visitor", example = "1234", required = true)
  val nomisPersonId: Long,
  @Schema(description = "indicates lead visitor for this visit", example = "true", required = true)
  val leadVisitor: Boolean,
) {

  constructor(visitVisitorEntity: VisitVisitor) : this(
    nomisPersonId = visitVisitorEntity.nomisPersonId,
    leadVisitor = visitVisitorEntity.leadVisitor,
  )
}
