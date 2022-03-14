package uk.gov.justice.digital.hmpps.visitscheduler.data

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitContact
import javax.validation.constraints.NotBlank

@Schema(description = "Contact")
data class ContactDto(
  @Schema(description = "Main Contact Name", example = "John Smith", required = true) @field:NotBlank val contactName: String,
  @Schema(description = "Main Contact Phone", example = "01234 567890", required = true) @field:NotBlank val contactPhone: String,
) {

  constructor(visitContactEntity: VisitContact) : this(
    contactName = visitContactEntity.contactName,
    contactPhone = visitContactEntity.contactPhone,
  )
}
