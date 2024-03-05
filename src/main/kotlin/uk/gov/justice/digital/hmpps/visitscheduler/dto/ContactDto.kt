package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitContact
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationContact
import uk.gov.justice.digital.hmpps.visitscheduler.validation.NullableNotBlank

@Schema(description = "Contact")
data class ContactDto(
  @Schema(description = "Contact Name", example = "John Smith", required = true)
  @field:NotBlank
  val name: String,
  @Schema(description = "Contact Phone Number", example = "01234 567890", required = false)
  @field:NullableNotBlank
  val telephone: String?,
) {
  constructor(entity: VisitContact) : this(
    name = entity.name,
    telephone = entity.telephone,
  )

  constructor(entity: ApplicationContact) : this(
    name = entity.name,
    telephone = entity.telephone,
  )
}
