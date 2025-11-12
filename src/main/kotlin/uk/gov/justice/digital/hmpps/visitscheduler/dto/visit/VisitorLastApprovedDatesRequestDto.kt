package uk.gov.justice.digital.hmpps.visitscheduler.dto.visit

import io.swagger.v3.oas.annotations.media.Schema

data class VisitorLastApprovedDatesRequestDto(
  @param:Schema(description = "List of Nomis Person Ids for whom last visit approved date is needed", required = true)
  val nomisPersonIds: List<Long>,
)
