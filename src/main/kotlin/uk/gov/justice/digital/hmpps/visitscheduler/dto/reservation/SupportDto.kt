package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Support
import javax.validation.constraints.NotBlank

@Schema(description = "Visitor support")
data class SupportDto(
  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,
  @Schema(description = "Support text description", example = "visually impaired assistance", required = false)
  val text: String? = null
) {

  constructor(entity: Support) : this(
    type = entity.type,
    text = entity.text,
  )
}
