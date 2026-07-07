package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDateDto

@Schema(description = "Session schedule that has future date exclusions")
data class SessionSchedulesWithDateExclusionsDto(
  @param:Schema(description = "Session schedule details that have future date exclusions", required = true)
  val sessionScheduleDto: SessionScheduleDto,

  @param:NotEmpty
  @param:Schema(description = "Future exclude dates for the session.", required = true)
  val excludeDates: List<ExcludeDateDto>,
)
