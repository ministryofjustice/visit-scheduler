package uk.gov.justice.digital.hmpps.visitscheduler.dto

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import java.time.LocalDateTime

data class UpdateVisitFromExternalSystemDto(
  @param:Schema(description = "Client name. Must exactly match the client that created the visit.", required = true)
  @field:NotBlank
  val clientName: String,
  @param:JsonAlias("username")
  @param:Schema(description = "Identity of the user who actioned this request. Also accepts username as an input alias. Required and nonblank for STAFF (username, maximum 60 characters) or PUBLIC (booker reference). Ignored for PRISONER (including omitted or null userType), which always uses the visit's prisoner ID (maximum 60 characters). SYSTEM uses the shared system actor.", required = false)
  val actionedBy: String? = null,
  @param:Schema(description = "Application method. Defaults to BY_PRISONER when omitted or null.", required = false)
  val applicationMethodType: ApplicationMethodType? = null,
  @param:Schema(description = "User type for user who actioned this request. Defaults to PRISONER when omitted or null.", required = false)
  val userType: UserType? = null,
  @param:Schema(description = "Visit Room", example = "A1", required = true)
  @field:NotBlank
  val visitRoom: String,
  @param:Schema(description = "Visit type. SOCIAL_VIDEO is supported only through external-system visit endpoints. The existing visit type cannot be changed.", example = "SOCIAL", required = true)
  @field:NotNull
  val visitType: VisitType,
  @param:Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val visitRestriction: VisitRestriction,
  @param:Schema(description = "The date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val startTimestamp: LocalDateTime,
  @param:Schema(description = "The finishing date and time of the visit", example = "2018-12-01T13:45:00", required = true)
  @field:NotNull
  val endTimestamp: LocalDateTime,
  @param:Schema(description = "Visit Notes")
  @field:Valid
  val visitNotes: List<VisitNoteDto> = emptyList(),
  @param:Schema(description = "Contact associated with the visit", required = true)
  @field:Valid
  val visitContact: ContactDto,
  @param:Schema(description = "List of visitors associated with the visit", required = false)
  @field:Valid
  val visitors: Set<VisitorDto>? = setOf(),
  @param:Schema(description = "Additional support associated with the visit")
  @field:Valid
  val visitorSupport: VisitorSupportDto? = null,
)
