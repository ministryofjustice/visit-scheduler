package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class SessionDateBlockedDto(
  @NotNull
  val sessionTemplateReference: String,
  @NotNull
  val visitDate: LocalDate,
)
