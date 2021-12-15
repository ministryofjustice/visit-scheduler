package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDate
import java.time.LocalTime
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateSessionTemplateRequest(
  @Schema(description = "prisonId", example = "MDI", required = true) @NotBlank val prisonId: String,
  @Schema(
    description = "The start time of the generated visit session(s)",
    example = "13:45",
    required = true
  ) @NotNull val startTime: LocalTime,
  @Schema(
    description = "The end time of the generated visit session(s)",
    example = "13:45",
    required = true
  ) @NotNull val endTime: LocalTime,
  @Schema(
    description = "The start date of the session template",
    example = "2019-12-02",
    required = true
  ) @NotNull val startDate: LocalDate,
  @Schema(
    description = "The expiry date of the session template",
    example = "2019-12-02",
    required = true
  ) val expiryDate: LocalDate?,
  @Schema(description = "visit type", example = "STANDARD_SOCIAL", required = true) @NotBlank val visitType: VisitType,
  @Schema(description = "visit room", example = "A1", required = true) @NotBlank val visitRoom: String,
  @Schema(description = "restrictions", required = false) val restrictions: String?,
  @Schema(description = "frequency", required = false) @NotBlank val frequency: SessionFrequency,
  @Schema(description = "closed capacity", example = "STANDARD_SOCIAL", required = true) @NotBlank val closedCapacity: Int,
  @Schema(description = "open capacity", example = "STANDARD_SOCIAL", required = true) @NotBlank val openCapacity: Int,
)
