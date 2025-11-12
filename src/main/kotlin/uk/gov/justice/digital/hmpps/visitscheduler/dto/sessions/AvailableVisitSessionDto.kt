package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import java.time.LocalDate

@Schema(description = "Visit Session")
data class AvailableVisitSessionDto(
  @param:Schema(description = "Session date", example = "2020-11-01", required = true)
  @field:NotNull
  @param:FutureOrPresent
  val sessionDate: LocalDate,

  @param:Schema(description = "Session time slot", required = true)
  @field:NotNull
  @param:Valid
  val sessionTimeSlot: SessionTimeSlotDto,

  @param:Schema(description = "sessionTemplateReference", example = "v9d.7ed.7u", required = true)
  val sessionTemplateReference: String,

  @param:Schema(description = "Session Restriction", example = "OPEN", required = true)
  @field:NotNull
  val sessionRestriction: SessionRestriction,
) {
  constructor(visitSession: VisitSessionDto, sessionRestriction: SessionRestriction) : this(
    sessionTemplateReference = visitSession.sessionTemplateReference,
    sessionDate = visitSession.startTimestamp.toLocalDate(),
    sessionTimeSlot = SessionTimeSlotDto(visitSession.startTimestamp.toLocalTime(), visitSession.endTimestamp.toLocalTime()),
    sessionRestriction = sessionRestriction,
  )
}
