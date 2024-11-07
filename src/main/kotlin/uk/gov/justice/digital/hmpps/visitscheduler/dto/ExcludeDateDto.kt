package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.FutureOrPresent
import java.time.LocalDate

@Schema(description = "Prison exclude date")
data class ExcludeDateDto(
  @Valid
  @Schema(description = "exclude date", required = true)
  @FutureOrPresent
  val excludeDate: LocalDate,

  @Schema(description = "actioned by", required = true)
  val actionedBy: String,
)
