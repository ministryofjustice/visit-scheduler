package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prison update dto")
data class UpdatePrisonDto(
  @Schema(description = "minimum number of days notice from the current date to booked a visit", example = "2", required = false)
  var policyNoticeDaysMin: Int?,
  @Schema(description = "maximum number of days notice from the current date to booked a visit", example = "28", required = false)
  var policyNoticeDaysMax: Int?,
)
