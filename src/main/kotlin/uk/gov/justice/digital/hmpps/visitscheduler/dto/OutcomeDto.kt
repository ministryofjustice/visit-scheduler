package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus

@Schema(description = "Visit Outcome")
class OutcomeDto(
  @param:Schema(description = "Outcome Status", example = "VISITOR_CANCELLED", required = true)
  @field:NotNull
  val outcomeStatus: OutcomeStatus,
  @param:Schema(description = "Outcome text", example = "Because he got covid", required = false)
  val text: String? = null,
)
