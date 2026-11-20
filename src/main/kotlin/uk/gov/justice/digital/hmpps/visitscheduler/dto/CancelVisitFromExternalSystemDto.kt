package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

data class CancelVisitFromExternalSystemDto(
  @param:Schema(description = "Client name. Must exactly match the client that created the visit.", required = true)
  @field:NotBlank
  val clientName: String,

  @param:Schema(description = "Outcome - status and text", required = true)
  @field:Valid
  val cancelOutcome: OutcomeDto,

  @param:Schema(description = "Required and nonblank for STAFF (username, maximum 60 characters) or PUBLIC (booker reference). Ignored for PRISONER (uses the visit's prisoner ID) and SYSTEM (uses the shared system actor).", required = false)
  val actionedBy: String? = null,

  @param:Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  var userType: UserType,

  @param:Schema(description = "Application method", required = true)
  @field:NotNull
  var applicationMethodType: ApplicationMethodType,
)
