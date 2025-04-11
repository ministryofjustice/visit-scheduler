package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

@Schema(description = "Prison / Session Template user client dto")
data class UserClientDto(

  @Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  val userType: UserType,

  @Schema(description = "is prison user client active", example = "true", required = true)
  @field:NotNull
  var active: Boolean,
)
