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
  @param:Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @param:JsonProperty("prisonId")
  @param:Schema(description = "Prison Id", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,
  @param:Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @param:Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @param:Schema(description = "Visit Status", example = "RESERVED", required = true)
  @field:NotNull
  val visitStatus: VisitStatus,
  @param:Schema(description = "Outcome Status", defaultValue = "NOT_RECORDED", required = false)
  val outcomeStatus: OutcomeStatus? = OutcomeStatus.NOT_RECORDED,
  @param:Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @param:Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @param:Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @param:Schema(description = "The date and time of when the visit was created in NOMIS", example = "2018-12-01T13:45:00", required = false)
  val createDateTime: LocalDateTime? = null,
  @param:Schema(description = "The date and time of when the visit was modified in NOMIS", example = "2018-12-10T13:45:00", required = false)
  val modifyDateTime: LocalDateTime? = null,
  @param:Schema(description = "Create legacy data", required = false)
  val legacyData: CreateLegacyDataRequestDto? = null,
  @param:Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: CreateLegacyContactOnVisitRequestDto? = CreateLegacyContactOnVisitRequestDto.create(),
  @param:Schema(description = "List of visitors associated with the visit", required = false)
  val visitors: Set<@Valid VisitorDto>? = setOf(),
  @param:Schema(description = "Visit notes", required = false)
  val visitNotes: Set<@Valid VisitNoteDto>? = setOf(),
  @param:Schema(description = "Username for user who actioned this request", required = false)
  val actionedBy: String? = NOT_KNOWN_NOMIS,
)
