package uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class SessionVisitCountsByDateDto(
  @param:Schema(description = "Date of Report", example = "2023-09-01", required = true)
  @field:NotNull
  val reportDate: LocalDate,

  @param:Schema(description = "Prison code", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @param:Schema(description = "If the prison had blocked the date for visits", example = "true", required = true)
  @field:NotNull
  val isBlockedDate: Boolean,

  @param:Schema(description = "False if no sessions on the date for prison, true otherwise", example = "true", required = true)
  @field:NotNull
  val hasSessionsOnDate: Boolean,

  @param:Schema(description = "Session count details", required = false)
  var visitCountBySession: VisitCountBySessionDto?,
)
