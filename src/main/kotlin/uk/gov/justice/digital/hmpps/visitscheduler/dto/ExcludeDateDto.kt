package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.FutureOrPresent
import java.time.LocalDate

@Schema(description = "Prison exclude date")
data class ExcludeDateDto(
  @param:Valid
  @param:Schema(description = "exclude date", required = true)
  @param:FutureOrPresent
  val excludeDate: LocalDate,

  @param:Schema(description = "actioned by", required = true)
  val actionedBy: String,
)
