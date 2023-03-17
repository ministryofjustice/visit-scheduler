package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport

@Schema(description = "Visitor support")
data class VisitorSupportDto(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null,
) {

  constructor(visitSupportEntity: VisitSupport) : this(
    type = visitSupportEntity.type,
    text = visitSupportEntity.text,
  )
}
