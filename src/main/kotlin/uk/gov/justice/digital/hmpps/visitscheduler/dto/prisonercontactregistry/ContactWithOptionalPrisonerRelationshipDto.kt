package uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "A contact with an optional prisoner relationship")
data class ContactWithOptionalPrisonerRelationshipDto(
  @param:Schema(description = "Identifier for this contact (Person in NOMIS)", example = "5871791")
  val contactId: Long,

  @param:Schema(description = "First name", example = "John", required = true)
  val firstName: String,

  @param:Schema(description = "Middle name", example = "Mark", required = false)
  val middleName: String? = null,

  @param:Schema(description = "Last name", example = "Smith", required = true)
  val lastName: String,

  @param:Schema(description = "Date of birth", example = "1980-01-28", required = false)
  val dateOfBirth: LocalDate? = null,

  @param:Schema(description = "Code for relationship to Prisoner", example = "RO", required = false)
  val relationshipCode: String? = null,

  @param:Schema(description = "Description of relationship to Prisoner", example = "Responsible Officer", required = false)
  val relationshipDescription: String? = null,

  @param:Schema(description = "Type of Contact", example = "O", required = false)
  val contactType: String? = null,

  @param:Schema(description = "Description of Contact Type", example = "Official", required = false)
  val contactTypeDescription: String? = null,

  @param:Schema(description = "List of restrictions associated with the contact", required = false)
  val restrictions: List<RestrictionDto> = listOf(),

  @param:Schema(description = "Is this prisoner's contact relationship approved?", example = "true")
  val approvedVisitor: Boolean? = null,
)
