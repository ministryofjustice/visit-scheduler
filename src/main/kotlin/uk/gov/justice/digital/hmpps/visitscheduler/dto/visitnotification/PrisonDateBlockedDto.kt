package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class PrisonDateBlockedDto(
  @field:NotNull
  val prisonCode: String,
  @field:NotNull
  val visitDate: LocalDate,
)
