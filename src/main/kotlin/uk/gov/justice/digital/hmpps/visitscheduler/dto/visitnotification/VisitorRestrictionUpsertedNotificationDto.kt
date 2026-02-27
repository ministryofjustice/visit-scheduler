package uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class VisitorRestrictionUpsertedNotificationDto(
  @field:NotBlank
  val visitorId: String,
  @field:NotNull
  val validFromDate: LocalDate,
  @param:JsonInclude(Include.NON_NULL)
  val validToDate: LocalDate? = null,
  @field:NotBlank
  val restrictionType: String,
  @field:NotBlank
  val restrictionId: String,
)
