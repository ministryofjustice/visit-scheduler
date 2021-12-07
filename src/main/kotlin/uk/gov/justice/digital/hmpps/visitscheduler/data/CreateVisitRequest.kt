package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
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
  @Schema(description = "visit room", example = "A1", required = true) @NotBlank val visitRoom: String,
  @Schema(description = "reasonable adjustments", required = false) val reasonableAdjustments: String,
  @Schema(description = "contact Id list", example = "12345", required = false) val contactIdList: List<CreateVisitorOnVisit>?,
  @Schema(description = "sessionId identifying the visit session template", example = "MDI", required = false) val sessionId: Long?,
)

data class CreateVisitorOnVisit(
  @Schema(description = "contact Id (personId NOMIS)", example = "1234556", required = true) @NotNull val contactId: Long,
  @Schema(description = "leadVisitor", example = "true", required = false, defaultValue = "false") val leadVisitor: Boolean = false
)
