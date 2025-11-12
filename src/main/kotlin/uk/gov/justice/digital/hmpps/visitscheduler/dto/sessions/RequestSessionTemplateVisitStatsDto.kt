package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class RequestSessionTemplateVisitStatsDto(
  @param:Schema(description = "Visits from date - for stats", example = "2019-11-02", required = true)
  @field:NotNull
  val visitsFromDate: LocalDate,

  @param:Schema(description = "Visits to date - for stats", example = "2019-11-30", required = false)
  val visitsToDate: LocalDate? = null,
)
