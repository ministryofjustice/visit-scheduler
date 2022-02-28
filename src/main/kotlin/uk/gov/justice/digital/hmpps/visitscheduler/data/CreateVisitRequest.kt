package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDateTime
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateVisitRequest(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true) @field:NotBlank val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true) @field:NotBlank val prisonId: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotNull val startTimestamp: LocalDateTime,
  @Schema(
    description = "The finishing date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotNull val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Type", example = "STANDARD_SOCIAL", required = true) @NotNull val visitType: VisitType,
  @Schema(description = "Visit Status", example = "RESERVED", required = true) @NotNull val visitStatus: VisitStatus,
  @Schema(description = "Visit Room", example = "A1", required = true) @field:NotBlank val visitRoom: String,
  @Schema(description = "Reasonable Adjustments", required = false) val reasonableAdjustments: String? = null,
  @Schema(description = "Main Contact associated with the visit", required = false) @field:Valid val mainContact: CreateContactOnVisitRequest?,
  @Schema(description = "List of visitors associated with the visit", required = false) val contactList: List<@Valid CreateVisitorOnVisitRequest>? = listOf(),
  @Schema(description = "Session Id identifying the visit session template", example = "123456", required = false) val sessionId: Long? = null,
)
