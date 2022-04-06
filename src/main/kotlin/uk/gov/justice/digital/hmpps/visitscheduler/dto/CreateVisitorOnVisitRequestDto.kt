package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotNull

data class CreateVisitorOnVisitRequestDto(
  @Schema(description = "NOMIS person ID", example = "1234556", required = true)
  @field:NotNull
  val nomisPersonId: Long,
)
