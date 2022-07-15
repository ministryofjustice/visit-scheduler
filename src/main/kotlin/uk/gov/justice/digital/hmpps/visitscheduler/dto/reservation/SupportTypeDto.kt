package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SupportType
import javax.validation.constraints.NotBlank

@Schema(description = "Support Type")
data class SupportTypeDto(

  @Schema(description = "Support type", example = "OTHER", required = true)
  @field:NotBlank
  val type: String,

  @Schema(description = "Support description", example = "visually impaired assistance", required = false)
  val text: String? = null,
) {
  constructor(supportType: SupportType) : this(
    type = supportType.name,
    text = supportType.description
  )
}
