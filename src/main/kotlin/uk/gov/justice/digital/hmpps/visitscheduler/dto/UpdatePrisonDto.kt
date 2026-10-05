package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import java.time.DayOfWeek

@Schema(description = "Prison update dto")
data class UpdatePrisonDto(
  @param:Schema(description = "Max number of total visitors")
  @field:Min(1)
  val maxTotalVisitors: Int?,
  @param:Schema(description = "Max number of adults")
  @field:Min(1)
  val maxAdultVisitors: Int?,
  @param:Schema(description = "Max number of children, if -1 then no limit is applied")
  @field:Min(-1)
  val maxChildVisitors: Int?,
  @param:Schema(description = "Age of adults in years")
  val adultAgeYears: Int?,
  @field:Valid
  @param:Schema(description = "The week day of which the prison week starts on. Enum value, any day of the week MONDAY - SUNDAY")
  var weekStartDay: DayOfWeek?,
  @param:Schema(description = "The limit per prison week, the number of remand visits that can be booked per week")
  @field:Min(1)
  var remandVisitLimitPerWeek: Int?,
  @param:Schema(description = "prison user client", required = false)
  val clients: List<PrisonUserClientDto>?,
)
