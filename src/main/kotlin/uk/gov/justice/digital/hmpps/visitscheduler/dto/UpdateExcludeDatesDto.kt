package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Support Type")
data class UpdateExcludeDatesDto(
  val excludeDates: List<ExcludeDatesDto>,
)
