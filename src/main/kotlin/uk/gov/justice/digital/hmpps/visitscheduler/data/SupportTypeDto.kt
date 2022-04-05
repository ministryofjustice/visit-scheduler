package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SupportType

@Schema(description = "Support Type")
data class SupportTypeDto(

  @Schema(description = "Support type name", example = "MASK_EXEMPT", required = true)
  val type: String,

  @Schema(description = "Support description", example = "Face covering exemption", required = true)
  val description: String,
) {
  constructor(supportType: SupportType) : this(
    type = supportType.name,
    description = supportType.description
  )
}
