package uk.gov.justice.digital.hmpps.visitscheduler.dto.application

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.controller.validators.VisitorContactValidation
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.UserType
import java.time.LocalDate

data class CreateApplicationDto(
  @Schema(description = "Prisoner Id", example = "AF34567G", required = true)
  @field:NotBlank
  val prisonerId: String,
  @Schema(description = "Session template reference", example = "v9d.7ed.7u", required = true)
  @field:NotBlank
  val sessionTemplateReference: String,
  @Schema(description = "The date for the visit", example = "2018-12-01", required = true)
  @field:NotNull
  val sessionDate: LocalDate,
  @Schema(description = "Visit Restriction", example = "OPEN", required = true)
  @field:NotNull
  val applicationRestriction: CreateApplicationRestriction,
  @Schema(description = "Contact associated with the visit", required = false)
  @field:Valid
  val visitContact: ContactDto?,
  @Schema(description = "List of visitors associated with the visit", required = true)
  @field:NotEmpty
  @field:VisitorContactValidation
  var visitors: Set<@Valid VisitorDto>,
  @Schema(description = "additional support associated with the visit", required = false)
  @Valid
  var visitorSupport: ApplicationSupportDto? = null,
  @Schema(description = "Username for user who actioned this request", required = true)
  val actionedBy: String,
  @Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  val userType: UserType,
)
