package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison

@Schema(description = "Prison dto")
data class PrisonDto(

  @param:Schema(description = "prison code", example = "BHI", required = true)
  @field:NotNull
  var code: String,

  @param:Schema(description = "is prison active", example = "true", required = true)
  @field:NotNull
  var active: Boolean = false,

  @param:Schema(description = "Max number of total visitors")
  @field:NotNull
  @field:Min(1)
  val maxTotalVisitors: Int,
  @param:Schema(description = "Max number of adults")
  @field:NotNull
  @field:Min(1)
  val maxAdultVisitors: Int,
  @param:Schema(description = "Max number of children")
  @field:NotNull
  @field:Min(0)
  val maxChildVisitors: Int,
  @param:Schema(description = "Age of adults in years")
  @field:NotNull
  val adultAgeYears: Int,
  @param:Schema(description = "prison user client", required = false)
  val clients: List<@Valid PrisonUserClientDto> = mutableListOf(),
) {
  constructor(prisonEntity: Prison) : this(
    code = prisonEntity.code,
    active = prisonEntity.active,
    maxTotalVisitors = prisonEntity.maxTotalVisitors,
    maxAdultVisitors = prisonEntity.maxAdultVisitors,
    maxChildVisitors = prisonEntity.maxChildVisitors,
    adultAgeYears = prisonEntity.adultAgeYears,
    clients = prisonEntity.clients.map { prisonUserClient ->
      PrisonUserClientDto(prisonUserClient)
    }.toList(),
  )
}
