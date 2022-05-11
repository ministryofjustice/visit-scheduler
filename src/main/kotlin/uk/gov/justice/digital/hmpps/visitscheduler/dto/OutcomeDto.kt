package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import javax.validation.constraints.NotNull

@Schema(description = "Visit Outcome")
class OutcomeDto(
  @Schema(description = "Outcome Status", example = "VISITOR_CANCELLED", required = true)
  @field:NotNull
  val outcomeStatus: OutcomeStatus,
  @Schema(description = "Outcome text", example = "Because he got covid", required = false)
  val text: String? = null
)
