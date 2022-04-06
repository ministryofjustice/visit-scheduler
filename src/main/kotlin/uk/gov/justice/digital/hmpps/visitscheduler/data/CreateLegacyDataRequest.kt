package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import javax.validation.constraints.NotNull

data class CreateLegacyDataRequest(

  @Schema(description = "NOMIS lead visitor ID", example = "1234556", required = true)
  @field:NotNull
  val leadVisitorId: Long
)
