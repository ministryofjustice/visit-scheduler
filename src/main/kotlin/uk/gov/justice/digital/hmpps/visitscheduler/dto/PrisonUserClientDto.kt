package uk.gov.justice.digital.hmpps.visitscheduler.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient

@Schema(description = "Prison / Session Template user client dto")
data class PrisonUserClientDto(
  @param:Schema(description = "minimum number of days notice from the current date to booked a visit", example = "2", required = true)
  @field:NotNull
  @field:Min(0)
  val policyNoticeDaysMin: Int,

  @param:Schema(description = "maximum number of days notice from the current date to booked a visit", example = "28", required = true)
  @field:NotNull
  @field:Min(0)
  val policyNoticeDaysMax: Int,

  @param:Schema(description = "User type", example = "STAFF", required = true)
  @field:NotNull
  val userType: UserType,

  @param:Schema(description = "is prison user client active", example = "true", required = true)
  @field:NotNull
  var active: Boolean,
) {
  constructor(prisonUserClient: PrisonUserClient) : this(
    userType = prisonUserClient.userType,
    policyNoticeDaysMin = prisonUserClient.policyNoticeDaysMin,
    policyNoticeDaysMax = prisonUserClient.policyNoticeDaysMax,
    active = prisonUserClient.active,
  )
}
