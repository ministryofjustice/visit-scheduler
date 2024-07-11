package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.service.NOT_KNOWN_NOMIS
import java.time.LocalDateTime

@Schema(description = "Migrate visit request")
data class MigrateVisitRequestDto(
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
  @Schema(description = "The date and time of when the visit was created in NOMIS", example = "2018-12-01T13:45:00", required = false)
  val createDateTime: LocalDateTime? = null,
  @Schema(description = "The date and time of when the visit was modified in NOMIS", example = "2018-12-10T13:45:00", required = false)
  val modifyDateTime: LocalDateTime? = null,
  @Schema(description = "Create legacy data", required = false)
  val legacyData: CreateLegacyDataRequestDto? = null,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: CreateLegacyContactOnVisitRequestDto? = CreateLegacyContactOnVisitRequestDto.create(),
  @Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: Set<@Valid VisitorDto>? = setOf(),
  @Schema(description = "Visit notes", required = false)
  val visitNotes: Set<@Valid VisitNoteDto>? = setOf(),
  @Schema(description = "Username for user who actioned this request", required = false)
  val actionedBy: String? = NOT_KNOWN_NOMIS,
)
