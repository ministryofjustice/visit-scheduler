package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

class BookingRequest(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: ContactDto?,
  @Schema(description = "List of visitors associated with the visit", required = true)
  @field:NotEmpty
  val visitors: Set<@Valid VisitorDto>,
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: Set<@Valid SupportTypeDto>? = setOf(),
)
