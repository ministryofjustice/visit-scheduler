package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import java.time.LocalDateTime
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

@Schema(description = "Migrate visit request")
data class MigrateVisitRequestDto(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @Schema(description = "Prison Id", example = "MDI", required = true)
  @field:NotBlank
  val prisonId: String,
  @Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "RESERVED", required = true)
  @field:NotNull
  val visitStatus: VisitStatus,
  @Schema(description = "Outcome Status", defaultValue = "NOT_RECORDED", required = false)
  val outcomeStatus: OutcomeStatus? = OutcomeStatus.NOT_RECORDED,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "Create legacy data", required = false)
  val legacyData: CreateLegacyDataRequestDto? = null,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: CreateLegacyContactOnVisitRequestDto? = CreateLegacyContactOnVisitRequestDto(),
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: Set<@Valid CreateVisitorOnVisitRequestDto>? = setOf(),
  @Schema(description = "Visit notes", required = false)
  val visitNotes: Set<@Valid VisitNoteDto>? = setOf(),
)
