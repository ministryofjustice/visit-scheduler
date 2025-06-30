package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

@Schema(description = "Visit")
data class VisitDto(
  @Schema(description = "Application Reference", example = "dfs-wjs-eqr", required = false)
  val applicationReference: String?,
  @Schema(description = "session template Reference", example = "dfs-wjs-eqr", required = false)
  val sessionTemplateReference: String? = null,
  @Schema(description = "Visit Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @JsonProperty("prisonId")
  @Schema(description = "Prison Id", example = "MDI", required = true)
  val prisonCode: String,
  @Schema(description = "Visit Room", example = "Visits Main Hall", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "Visit Type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "Visit Status", example = "BOOKED", required = true)
  val visitStatus: VisitStatus,
  @Schema(description = "Visit Sub Status", example = "AUTO_APPROVED", required = true)
  val visitSubStatus: VisitSubStatus,
  @Schema(description = "Outcome Status", example = "VISITOR_CANCELLED", required = false)
  val outcomeStatus: OutcomeStatus? = null,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: VisitRestriction,
  @Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @Schema(description = "Visit Notes", required = false)
  val visitNotes: List<VisitNoteDto> = listOf(),
  @Schema(description = "Contact associated with the visit", required = true)
  val visitContact: ContactDto,
  @Schema(description = "List of visitors associated with the visit", required = true)
  val visitors: List<VisitorDto> = listOf(),
  @Schema(description = "Additional spport associated with the visit", required = false)
  val visitorSupport: VisitorSupportDto? = null,
  @Schema(description = "The visit created date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val createdTimestamp: LocalDateTime,
  @Schema(description = "The visit modified date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val modifiedTimestamp: LocalDateTime,
  @Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  val userType: UserType,
  @Schema(description = "Date the visit was first booked or migrated", example = "2018-12-01T13:45:00", required = false)
  var firstBookedDateTime: LocalDateTime? = null,
  @Schema(description = "External system details associated with the visit")
  val visitExternalSystemDetails: VisitExternalSystemDetailsDto?,
)
