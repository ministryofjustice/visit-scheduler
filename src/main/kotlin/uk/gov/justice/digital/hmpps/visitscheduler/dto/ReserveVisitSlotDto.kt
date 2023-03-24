package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.VisitorContactValidation
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.VisitorCountValidation
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDateTime

data class ReserveVisitSlotDto(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @JsonProperty("prisonId")
  @Schema(description = "Prison Id", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,
  @Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: ContactDto?,
  @Schema(description = "List of visitors associated with the visit", required = true)
  @field:NotEmpty
  @field: VisitorCountValidation
  @field:VisitorContactValidation
  var visitors: Set<@Valid VisitorDto>,
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: Set<@Valid VisitorSupportDto>? = setOf(),
  @Schema(description = "Username for user who actioned this request", required = true)
  val actionedBy: String,
)
