package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotNull

data class CreateVisitorOnVisitRequest(
  @Schema(description = "NOMIS person ID", example = "1234556", required = true) @field:NotNull val nomisPersonId: Long,
  @Schema(description = "Lead Visitor", example = "true", required = false, defaultValue = "false") val leadVisitor: Boolean = false
)
