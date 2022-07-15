package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note

@Schema(description = "VisitNote")
class NoteDto(
  @Schema(description = "Note type", example = "VISITOR_CONCERN", required = true)
  val type: NoteType,
  @Schema(description = "Note text", example = "Visitor is concerned that his mother in-law is coming!", required = true)
  val text: String
) {

  constructor(entity: Note) : this(
    type = entity.type,
    text = entity.text,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NoteDto

    if (type != other.type) return false

    return true
  }

  override fun hashCode(): Int {
    return type.hashCode()
  }
}
