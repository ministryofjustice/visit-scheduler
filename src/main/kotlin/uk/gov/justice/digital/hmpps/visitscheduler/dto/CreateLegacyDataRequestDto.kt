package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

data class CreateLegacyDataRequestDto(

  @param:Schema(description = "NOMIS lead visitor ID", example = "1234556", required = true)
  @field:NotNull
  val leadVisitorId: Long,
)
