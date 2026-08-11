package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class RestrictionDto(
  @param:Schema(description = "Restriction ID", example = "123", required = true)
  val restrictionId: Long,
  @param:Schema(description = "Restriction Type Code", example = "123", required = true)
  val restrictionType: String,
  @param:Schema(description = "Date from which the restriction applies", example = "2000-10-31", required = true)
  val startDate: LocalDate,
  @param:Schema(description = "Restriction Expiry", example = "2000-10-31", required = false)
  val expiryDate: LocalDate? = null,
)
