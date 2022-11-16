package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Prisoner details - only offenderNo and location needed")
open class PrisonerDetailDto(
  @Schema(description = "The prisoner's nomsId", example = "A0000AA")
  val nomsId: String?,

  @Schema(description = "Level 1 from the prisoner's internal location", example = "B")
  val unitCode1: String?,

  @Schema(description = "Level 2 from the prisoner's internal location", example = "3")
  val unitCode2: String?,

  @Schema(description = "Level 3 from the prisoner's internal location", example = "008")
  val unitCode3: String?
)
