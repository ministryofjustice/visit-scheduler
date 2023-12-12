package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class PrisonDateBlockedDto(
  @NotNull
  val prisonCode: String,
  @NotNull
  val visitDate: LocalDate,
)
