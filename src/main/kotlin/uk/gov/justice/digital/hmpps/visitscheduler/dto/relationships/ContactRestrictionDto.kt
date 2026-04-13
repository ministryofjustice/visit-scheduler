package uk.gov.justice.digital.hmpps.visitscheduler.dto.relationships

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class ContactRestrictionDto(
  @param:Schema(description = "Unique identifier for the contact restriction", example = "1")
  val contactRestrictionId: Long,

  @param:Schema(description = "Unique identifier for the contact", example = "123")
  val contactId: Long,

  @param:Schema(description = "Restriction type", example = "BAN")
  val restrictionType: String,

  @param:Schema(description = "Restriction start date", example = "2024-01-01")
  val startDate: LocalDate? = null,

  @param:Schema(description = "Restriction end date ", example = "2024-01-01")
  val expiryDate: LocalDate? = null,
)
