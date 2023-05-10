package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.level

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup

data class SessionIncentiveLevelGroupDto(
  @JsonProperty("name")
  @Schema(description = "Group name", example = "Enhanced", required = true)
  @field:NotBlank
  val name: String,

  @Schema(description = "Reference", example = "v9-d7-ed-7u", required = true)
  val reference: String,

  @Schema(description = "list of allowed incentive levels for group", required = false)
  val incentiveLevels: List<IncentiveLevel> = listOf(),
) {
  constructor(incentiveLevelGroup: SessionIncentiveLevelGroup) : this(
    name = incentiveLevelGroup.name,
    reference = incentiveLevelGroup.reference,
    incentiveLevels = incentiveLevelGroup.sessionIncentiveLevels.map { it.prisonerIncentiveLevel },
  )
}
