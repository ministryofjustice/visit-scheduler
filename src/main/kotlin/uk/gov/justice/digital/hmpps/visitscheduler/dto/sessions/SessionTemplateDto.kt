package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Shape
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.level.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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
  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  @Schema(description = "The start time of the generated visit session(s)", example = "13:45", required = true)
  val startTime: LocalTime,
  @Schema(description = "The end time of the generated visit session(s)", example = "13:45", required = true)
  @JsonFormat(pattern = "HH:mm", shape = Shape.STRING)
  val endTime: LocalTime,
  @Schema(description = "The start of the Validity period for the session template", example = "2019-12-02", required = true)
  @field:NotNull
  val validFromDate: LocalDate,
  @Schema(description = "The end of the Validity period for the session template", example = "2019-12-02", required = false)
  val validToDate: LocalDate? = null,
  @Schema(description = "visit type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @Schema(description = "biWeekly", example = "true", required = true)
  val biWeekly: Boolean,
  @Schema(description = "Visit Room", example = "A1 L3", required = true)
  @field:NotBlank
  val visitRoom: String,
  @Schema(description = "closed capacity", example = "10", required = true)
  val closedCapacity: Int,
  @Schema(description = "open capacity", example = "50", required = true)
  val openCapacity: Int,
  @Schema(description = "day of week for visit", example = "MONDAY", required = false)
  val dayOfWeek: DayOfWeek?,
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
    startTime = sessionTemplateEntity.startTime,
    endTime = sessionTemplateEntity.endTime,
    visitType = sessionTemplateEntity.visitType,
    validFromDate = sessionTemplateEntity.validFromDate,
    validToDate = sessionTemplateEntity.validToDate,
    visitRoom = sessionTemplateEntity.visitRoom,
    closedCapacity = sessionTemplateEntity.closedCapacity,
    openCapacity = sessionTemplateEntity.openCapacity,
    dayOfWeek = sessionTemplateEntity.dayOfWeek,
    permittedLocationGroups = sessionTemplateEntity.permittedSessionLocationGroups.map { SessionLocationGroupDto(it) },
    prisonerCategoryGroups = sessionTemplateEntity.permittedSessionCategoryGroups.map { SessionCategoryGroupDto(it) },
    prisonerIncentiveLevelGroups = sessionTemplateEntity.permittedSessionIncentiveLevelGroups.map { SessionIncentiveLevelGroupDto(it) },
    biWeekly = sessionTemplateEntity.biWeekly,
  )
}
