package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote

@Schema(description = "VisitNote")
class VisitNoteDto(
  @Schema(description = "Note type", example = "VISITOR_CONCERN", required = true)
  val type: VisitNoteType,
  @Schema(description = "Note text", example = "Visitor is concerned that his mother in-law is coming!", required = true)
  val text: String,
) {
  constructor(visitNoteEntity: VisitNote) : this(
    type = visitNoteEntity.type,
    text = visitNoteEntity.text,
  )

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VisitNoteDto

    if (type != other.type) return false

    return true
  }

  override fun hashCode(): Int = type.hashCode()
}
