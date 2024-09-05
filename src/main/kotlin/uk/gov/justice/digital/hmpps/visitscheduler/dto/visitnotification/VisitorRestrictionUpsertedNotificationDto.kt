package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class VisitorRestrictionUpsertedNotificationDto(
  @NotBlank
  val visitorId: String,
  @NotNull
  val validFromDate: LocalDate,
  @JsonInclude(Include.NON_NULL)
  val validToDate: LocalDate? = null,
  @NotBlank
  val restrictionType: String,
)
