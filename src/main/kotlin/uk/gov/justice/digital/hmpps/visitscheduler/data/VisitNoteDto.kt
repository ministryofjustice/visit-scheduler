package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitNoteType

@Schema(description = "VisitNote")
class VisitNoteDto(
  @Schema(description = "Note type", example = "VISITOR_CONCERN", required = true)
  val type: VisitNoteType,
  @Schema(description = "Note text", example = "Visitor is concerned that his mother in-law is coming!", required = true)
  val text: String
) {
  constructor(VisitNotesEntity: VisitNote) : this(
    type = VisitNotesEntity.type,
    text = VisitNotesEntity.text,
  )
}
