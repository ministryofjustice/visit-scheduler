package uk.gov.justice.digital.hmpps.visitscheduler.dto.application

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

@Schema(description = "Application")
data class ApplicationDto(
  @param:Schema(description = "application reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,
  @param:Schema(description = "session template Reference", example = "dfs-wjs-eqr", required = false)
  val sessionTemplateReference: String? = null,
  @param:Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  val prisonerId: String,
  @param:JsonProperty("prisonId")
  @param:Schema(description = "Prison Id", example = "MDI", required = true)
  val prisonCode: String,
  @param:Schema(description = "Visit Type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @param:Schema(description = "Visit Restriction", example = "OPEN", required = true)
  val visitRestriction: VisitRestriction,
  @param:Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @param:Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @param:Schema(description = "Visit Notes", required = false)
  val visitNotes: List<VisitNoteDto> = listOf(),
  @param:Schema(description = "Contact associated with the application", required = false)
  val visitContact: ContactDto? = null,
  @param:Schema(description = "List of visitors associated with the application", required = false)
  val visitors: List<VisitorDto> = listOf(),
  @param:Schema(description = "Additional support associated with the application", required = false)
  val visitorSupport: VisitorSupportDto? = null,
  @param:Schema(description = "The visit created date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val createdTimestamp: LocalDateTime,
  @param:Schema(description = "The visit modified date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val modifiedTimestamp: LocalDateTime,
  @param:Schema(description = "Is the application reserved", example = "true", required = true)
  @field:NotNull
  val reserved: Boolean,
  @param:Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  val userType: UserType,
  @param:Schema(description = "Application status", example = "IN_PROGRESS", required = true)
  @field:NotNull
  val applicationStatus: ApplicationStatus,
)
