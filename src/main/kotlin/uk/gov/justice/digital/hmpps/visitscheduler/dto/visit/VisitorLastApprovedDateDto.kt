package uk.gov.justice.digital.hmpps.visitscheduler.dto.visit

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class VisitorLastApprovedDateDto(
  @Schema(description = "Nomis Person Id", example = "1234", required = true)
  val nomisPersonId: Long,

  @Schema(description = "Last approved visit date", example = "2025-11-01", required = false)
  val lastApprovedVisitDate: LocalDate?,
)
