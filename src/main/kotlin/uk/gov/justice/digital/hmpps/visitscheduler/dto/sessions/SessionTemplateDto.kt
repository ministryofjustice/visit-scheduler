package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek

data class SessionTemplateDto(

  @Schema(description = "Reference", example = "v9d.7ed.7u", required = true)
  val reference: String,
  @Schema(description = "name", example = "Monday Session", required = true)
  @field:NotBlank
  val name: String,
  @JsonProperty("prisonId")
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,
  @Schema(description = "The time slot of the generated visit session(s)", required = true)
  val sessionTimeSlot: SessionTimeSlotDto,
  @Schema(description = "Validity period for the session template", required = true)
  val sessionDateRange: SessionDateRangeDto,
  @Schema(description = "visit type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "number of weeks until the weekly day is repeated", example = "1", required = true)
  val weeklyFrequency: Int,
  @Schema(description = "Visit Room", example = "A1 L3", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "session capacity", required = true)
  val sessionCapacity: SessionCapacityDto,
  @Schema(description = "day of week for visit", example = "MONDAY", required = false)
  val dayOfWeek: DayOfWeek?,
  @Schema(description = "is session template active", example = "true", required = true)
  val active: Boolean,
  @Schema(description = "list of permitted session location groups", required = false)
  val permittedLocationGroups: List<SessionLocationGroupDto> = listOf(),
  @Schema(description = "list of permitted prisoner category groups", required = false)
  val prisonerCategoryGroups: List<SessionCategoryGroupDto> = listOf(),
  @Schema(description = "list of permitted incentive level groups", required = false)
  val prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto> = listOf(),
) {
  constructor(sessionTemplateEntity: SessionTemplate) : this(
    reference = sessionTemplateEntity.reference,
    name = sessionTemplateEntity.name,
    prisonCode = sessionTemplateEntity.prison.code,
    sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplateEntity.startTime, endTime = sessionTemplateEntity.endTime),
    visitType = sessionTemplateEntity.visitType,
    sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplateEntity.validFromDate, validToDate = sessionTemplateEntity.validToDate),
    visitRoom = sessionTemplateEntity.visitRoom,
    sessionCapacity = SessionCapacityDto(closed = sessionTemplateEntity.closedCapacity, open = sessionTemplateEntity.openCapacity),
    dayOfWeek = sessionTemplateEntity.dayOfWeek,
    permittedLocationGroups = sessionTemplateEntity.permittedSessionLocationGroups.map { SessionLocationGroupDto(it) },
    prisonerCategoryGroups = sessionTemplateEntity.permittedSessionCategoryGroups.map { SessionCategoryGroupDto(it) },
    prisonerIncentiveLevelGroups = sessionTemplateEntity.permittedSessionIncentiveLevelGroups.map { SessionIncentiveLevelGroupDto(it) },
    weeklyFrequency = sessionTemplateEntity.weeklyFrequency,
    active = sessionTemplateEntity.active,
  )
}
