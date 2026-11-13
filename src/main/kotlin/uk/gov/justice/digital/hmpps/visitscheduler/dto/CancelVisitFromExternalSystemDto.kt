package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

data class CancelVisitFromExternalSystemDto(
  @param:Schema(description = "Optional client name. Omitted, null or blank values skip the ownership check. A nonblank value must exactly match the stored external client name, including for an already cancelled visit.", required = false, nullable = true)
  val clientName: String? = null,

  @param:Schema(description = "Outcome - status and text", required = true)
  @field:Valid
  val cancelOutcome: OutcomeDto,

  @param:Schema(description = "Username for user who actioned this request. Maximum 60 characters for STAFF or PRISONER users.", required = true)
  @field:NotBlank
  val actionedBy: String,

  @param:Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  var userType: UserType,

  @param:Schema(description = "Application method", required = true)
  @field:NotNull
  var applicationMethodType: ApplicationMethodType,
)
