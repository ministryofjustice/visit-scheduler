package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeType
import javax.validation.constraints.NotNull

@Schema(description = "Visit Outcome")
class OutcomeDto(
  @Schema(description = "Outcome type", example = "VISITOR_CANCELLED", required = true)
  @NotNull
  val outcome: OutcomeType,
  @Schema(description = "Outcome text", example = "Because he got covid", required = false)
  val text: String? = null
)
