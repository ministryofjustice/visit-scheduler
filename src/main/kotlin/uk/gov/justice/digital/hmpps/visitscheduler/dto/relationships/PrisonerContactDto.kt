package uk.gov.justice.digital.hmpps.visitscheduler.dto.relationships

import io.swagger.v3.oas.annotations.media.Schema

data class PrisonerContactDto(
  @param:Schema(description = "Prisoner number (NOMS ID)", example = "A1234BC")
  val prisonerNumber: String,

  @param:Schema(description = "The identifier for the prisoner contact relationship", example = "123456")
  val prisonerContactId: Long,

  @param:Schema(description = "Unique identifier for the contact", example = "123")
  val contactId: Long,
)
