package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDateTime
import javax.validation.constraints.NotBlank

data class CreateVisitRequest(
  @Schema(description = "prisonerId", example = "AF34567G", required = true) val prisonerId: String,
  @Schema(description = "prisonId", example = "MDI", required = true) val prisonId: String,
  @Schema(
    description = "The date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val startTimestamp: LocalDateTime,
  @Schema(
    description = "The finishing date and time of the visit",
    example = "2018-12-01T13:45:00",
    required = true
  ) @NotBlank val endTimestamp: LocalDateTime,
  @Schema(description = "visitType", example = "STANDARD_SOCIAL", required = true) val visitType: VisitType,
  @Schema(description = "visitRoom", example = "A1", required = true) val visitRoom: String,
)
