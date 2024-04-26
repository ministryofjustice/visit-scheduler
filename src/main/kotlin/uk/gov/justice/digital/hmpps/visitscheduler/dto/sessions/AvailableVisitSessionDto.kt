package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.SessionRestriction
import java.time.LocalDate

@Schema(description = "Visit Session")
data class AvailableVisitSessionDto(
  @Schema(description = "Session date", example = "2020-11-01", required = true)
  @field:NotNull
  @FutureOrPresent
  val sessionDate: LocalDate,

  @Schema(description = "Session time slot", required = true)
  @field:NotNull
  @Valid
  val sessionTimeSlot: SessionTimeSlotDto,

  @Schema(description = "Session Restriction", example = "OPEN", required = true)
  @field:NotNull
  val sessionRestriction: SessionRestriction,
) {
  constructor(visitSession: VisitSessionDto, sessionRestriction: SessionRestriction) : this(
    sessionDate = visitSession.startTimestamp.toLocalDate(),
    sessionTimeSlot = SessionTimeSlotDto(visitSession.startTimestamp.toLocalTime(), visitSession.endTimestamp.toLocalTime()),
    sessionRestriction = sessionRestriction,
  )
}
