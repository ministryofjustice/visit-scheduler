package uk.gov.justice.digital.hmpps.visitscheduler.data

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitVisitor

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Visit")
data class VisitorDto(
  @Schema(description = "Visit id", example = "123", required = true) val visitId: Long,
  @Schema(description = "contactId of the visitor", example = "1234", required = true) val contactId: Long,
  @Schema(description = "indicates lead visitor for this visit", example = "true", required = true) val leadVisitor: Boolean,
) {

  constructor(visitVisitorEntity: VisitVisitor) : this(
    visitId = visitVisitorEntity.id.visitId,
    contactId = visitVisitorEntity.id.contactId,
    leadVisitor = visitVisitorEntity.leadVisitor,
  )
}
