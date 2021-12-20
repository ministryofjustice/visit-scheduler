package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateVisitRequest(
  @Schema(description = "prisonerId", example = "AF34567G", required = true) @NotBlank val prisonerId: String,
  @Schema(description = "prisonId", example = "MDI", required = true) @NotBlank val prisonId: String,
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
  @Schema(description = "visit type", example = "STANDARD_SOCIAL", required = true) @NotBlank val visitType: VisitType,
  @Schema(description = "visit visitStatus", example = "RESERVED", required = true) @NotBlank val visitStatus: VisitStatus,
  @Schema(description = "visit room", example = "A1", required = true) @NotBlank val visitRoom: String,
  @Schema(description = "reasonable adjustments", required = false) val reasonableAdjustments: String?,
  @Schema(description = "contact list", required = false) val contactList: List<CreateVisitorOnVisit>?,
  @Schema(description = "sessionId identifying the visit session template", example = "MDI", required = false) val sessionId: Long?,
)

data class CreateVisitorOnVisit(
  @Schema(description = "NOMIS person ID", example = "1234556", required = true) @NotNull val nomisPersonId: Long,
  @Schema(description = "leadVisitor", example = "true", required = false, defaultValue = "false") val leadVisitor: Boolean = false
)
