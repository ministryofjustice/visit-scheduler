package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class SessionDateBlockedDto(
  @field:NotNull
  val sessionTemplateReference: String,
  @field:NotNull
  val visitDate: LocalDate,
)
