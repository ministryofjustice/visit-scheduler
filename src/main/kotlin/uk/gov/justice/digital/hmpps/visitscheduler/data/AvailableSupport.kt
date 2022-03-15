package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.Support

@Schema(description = "Available Support")
data class AvailableSupport(

  @Schema(description = "Support code", example = "1040", required = true)
  val code: Int,

  @Schema(description = "Support name", example = "MASK_EXEMPT", required = true)
  val name: String,

  @Schema(description = "Support description", example = "Face covering exemption", required = true)
  val description: String,
) {
  constructor(support: Support) : this(
    code = support.code,
    name = support.name,
    description = support.description
  )
}
