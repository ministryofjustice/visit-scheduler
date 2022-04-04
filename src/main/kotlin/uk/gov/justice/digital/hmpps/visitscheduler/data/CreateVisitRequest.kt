package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDateTime
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

data class CreateVisitRequest(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "STANDARD_SOCIAL", required = true)
  @NotNull
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "RESERVED", required = true)
  @NotNull
  val visitStatus: VisitStatus,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @NotNull
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: CreateContactOnVisitRequest?,
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: List<@Valid CreateVisitorOnVisitRequest>? = listOf(),
  @Schema(description = "List of additional support associated with the visit", required = false)
  val visitorSupport: List<@Valid CreateSupportOnVisitRequest>? = listOf(),
  @Schema(description = "Visit notes", required = false)
  val visitNotes: List<@Valid VisitNoteDto>? = listOf(),
)
