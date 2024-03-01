package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitSupport
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.ApplicationSupport

@Schema(description = "Visitor support")
data class VisitorSupportDto(
  @Schema(description = "Support text description", example = "visually impaired assistance", required = true)
  @Size(min = 3, max = 512)
  @NotBlank
  val description: String,
) {

  constructor(entity: VisitSupport) : this(
    description = entity.description,
  )

  constructor(entity: ApplicationSupport) : this(
    description = entity.description,
  )
}
