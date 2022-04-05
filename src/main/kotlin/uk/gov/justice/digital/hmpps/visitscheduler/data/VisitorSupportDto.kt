package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitSupport
import javax.validation.constraints.NotBlank

@Schema(description = "Visitor support")
data class VisitorSupportDto(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null
) {

  constructor(visitSupportEntity: VisitSupport) : this(
    type = visitSupportEntity.type,
    text = visitSupportEntity.text,
  )
}
