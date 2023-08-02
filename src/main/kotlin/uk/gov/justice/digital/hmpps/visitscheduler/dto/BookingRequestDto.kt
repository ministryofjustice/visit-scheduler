package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType

data class BookingRequestDto(
  @Schema(description = "Username for user who actioned this request", required = true)
  @field:NotNull
  val actionedBy: String,
  @Schema(description = "application method", required = true)
  @field:NotNull
  val applicationMethodType: ApplicationMethodType,
)
