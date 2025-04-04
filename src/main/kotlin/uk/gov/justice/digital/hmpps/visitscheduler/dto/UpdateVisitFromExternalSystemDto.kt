package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

data class UpdateVisitFromExternalSystemDto(
  @Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Notes")
  @field:Valid
  val visitNotes: List<VisitNoteDto> = emptyList(),
  @Schema(description = "Contact associated with the visit", required = true)
  @field:Valid
  val visitContact: ContactDto,
  @Schema(description = "List of visitors associated with the visit", required = false)
  @field:Valid
  val visitors: Set<VisitorDto>? = setOf(),
  @Schema(description = "Additional support associated with the visit")
  @field:Valid
  val visitorSupport: VisitorSupportDto? = null,
)

