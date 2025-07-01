package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType

data class BookingRequestDto(
  @Schema(description = "Username for user who actioned this request", required = true)
  @field:NotNull
  val actionedBy: String,
  @Schema(description = "application method", required = true)
  @field:NotNull
  val applicationMethodType: ApplicationMethodType,
  @Schema(description = "allow over booking method", required = false)
  @field:NotNull
  val allowOverBooking: Boolean = false,
  @Schema(description = "User type for user who actioned this request", required = true)
  @field:NotNull
  val userType: UserType,
  @Schema(description = "flag to determine if visit should be a request or instant booking", required = false)
  val isRequestBooking: Boolean? = false,
)
