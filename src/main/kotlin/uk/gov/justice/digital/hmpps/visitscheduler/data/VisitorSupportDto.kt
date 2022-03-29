package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitSupport
import javax.validation.constraints.NotBlank

@Schema(description = "Visitor support")
data class VisitorSupportDto(
  @Schema(description = "Support name", example = "OTHER", required = true) @field:NotBlank val supportName: String,
  @Schema(description = "Support details", example = "visually impaired assistance", required = false) val supportDetails: String? = null
) {

  constructor(visitSupportEntity: VisitSupport) : this(
    supportName = visitSupportEntity.supportName,
    supportDetails = visitSupportEntity.supportDetails,
  )
}
