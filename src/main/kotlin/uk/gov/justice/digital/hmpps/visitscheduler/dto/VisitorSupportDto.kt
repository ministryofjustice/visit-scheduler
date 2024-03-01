package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport

@Schema(description = "Visitor support")
data class VisitorSupportDto(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null,
) {

  constructor(entity: VisitSupport) : this(
    type = entity.type,
    text = entity.text,
  )

  constructor(entity: ApplicationSupport) : this(
    type = entity.type,
    text = entity.text,
  )
}
