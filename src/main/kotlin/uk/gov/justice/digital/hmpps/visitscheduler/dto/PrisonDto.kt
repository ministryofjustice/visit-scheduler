package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import java.time.LocalDate

@Schema(description = "Prison dto")
data class PrisonDto(

  @Schema(description = "prison code", example = "BHI", required = true)
  @field:NotNull
  var code: String,

  @Schema(description = "is prison active", example = "true", required = true)
  @field:NotNull
  var active: Boolean = false,

  @Schema(description = "minimum number of days notice from the current date to booked a visit", example = "2", required = true)
  @field:NotNull
  @field:Min(0)
  val policyNoticeDaysMin: Int,
  @Schema(description = "maximum number of days notice from the current date to booked a visit", example = "28", required = true)
  @field:NotNull
  @field:Min(0)
  val policyNoticeDaysMax: Int,

  @Schema(description = "Max number of total visitors")
  @field:NotNull
  @field:Min(1)
  val maxTotalVisitors: Int,
  @Schema(description = "Max number of adults")
  @field:NotNull
  @field:Min(1)
  val maxAdultVisitors: Int,
  @Schema(description = "Max number of children")
  @field:NotNull
  @field:Min(0)
  val maxChildVisitors: Int,
  @Schema(description = "Age of adults in years")
  @field:NotNull
  val adultAgeYears: Int,

  @Schema(description = "exclude dates", required = false)
  val excludeDates: Set<LocalDate> = mutableSetOf(),

  @Schema(description = "prison user client", required = false)
  val clients: List<PrisonUserClientDto> = mutableListOf(),

) {

  constructor(prisonEntity: Prison) : this(
    code = prisonEntity.code,
    active = prisonEntity.active,
    excludeDates = prisonEntity.excludeDates.map { it.excludeDate }.toSet(),
    policyNoticeDaysMin = prisonEntity.policyNoticeDaysMin,
    policyNoticeDaysMax = prisonEntity.policyNoticeDaysMax,
    maxTotalVisitors = prisonEntity.maxTotalVisitors,
    maxAdultVisitors = prisonEntity.maxAdultVisitors,
    maxChildVisitors = prisonEntity.maxChildVisitors,
    adultAgeYears = prisonEntity.adultAgeYears,
    clients = prisonEntity.clients.map { PrisonUserClientDto(it.userType, it.active) }.toList(),
  )
}
