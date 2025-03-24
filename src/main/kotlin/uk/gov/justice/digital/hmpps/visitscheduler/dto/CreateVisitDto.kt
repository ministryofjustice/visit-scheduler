package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

data class CreateVisitDto(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "3-character code, example = MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "Client visit reference", example = "Reference ID in the client system", required = true)
  @field:NotBlank
  val clientVisitReference: String,
  @Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "BOOKED", required = true)
  @field:NotNull
  val visitStatus: VisitStatus,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "The date and time of when the visit was created in NEXUS", example = "2018-12-01T13:45:00", required = false)
  val createDateTime: LocalDateTime? = null,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: Set<@Valid VisitorDto>? = setOf(),
  @Schema(description = "Username for user who actioned this request", required = false)
  val actionedBy: String?,
)
