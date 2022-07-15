package uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Contact
import javax.validation.constraints.NotBlank

@Schema(description = "Contact")
data class ContactDto(
  @Schema(description = "Contact Name", example = "John Smith", required = true)
  @field:NotBlank
  val name: String,
  @Schema(description = "Contact Phone Number", example = "01234 567890", required = true)
  @field:NotBlank
  val telephone: String,
) {

  constructor(entity: Contact) : this(
    name = entity.name,
    telephone = entity.telephone,
  )
}
