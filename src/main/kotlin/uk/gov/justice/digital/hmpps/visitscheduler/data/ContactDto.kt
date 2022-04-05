package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
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
  constructor(visitContactEntity: VisitContact) : this(
    name = visitContactEntity.name,
    telephone = visitContactEntity.telephone,
  )
}
