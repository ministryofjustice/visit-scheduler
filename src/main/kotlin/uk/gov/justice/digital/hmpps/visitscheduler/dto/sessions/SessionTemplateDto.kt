package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionTemplateVisitOrderRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek

data class SessionTemplateDto(

  @param:Schema(description = "Reference", example = "v9d.7ed.7u", required = true)
  val reference: String,
  @param:Schema(description = "name", example = "Monday Session", required = true)
  @field:NotBlank
  val name: String,
  @param:JsonProperty("prisonId")
  @param:Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,
  @param:Schema(description = "The time slot of the generated visit session(s)", required = true)
  val sessionTimeSlot: SessionTimeSlotDto,
  @param:Schema(description = "Validity period for the session template", required = true)
  val sessionDateRange: SessionDateRangeDto,
  @param:Schema(description = "visit type", example = "SOCIAL", required = true)
  val visitType: VisitType,
  @param:Schema(description = "number of weeks until the weekly day is repeated", example = "1", required = true)
  val weeklyFrequency: Int,
  @param:Schema(description = "Visit Room", example = "A1 L3", required = true)
  @field:NotBlank
  val visitRoom: String,
  @param:Schema(description = "session capacity", required = true)
  val sessionCapacity: SessionCapacityDto,
  @param:Schema(description = "day of week for visit", example = "MONDAY", required = true)
  val dayOfWeek: DayOfWeek,
  @param:Schema(description = "is session template active", example = "true", required = true)
  val active: Boolean,
  @param:Schema(description = "list of permitted session location groups", required = false)
  val permittedLocationGroups: List<SessionLocationGroupDto> = listOf(),
  @param:Schema(description = "list of permitted prisoner category groups", required = false)
  val prisonerCategoryGroups: List<SessionCategoryGroupDto> = listOf(),
  @param:Schema(description = "list of permitted incentive level groups", required = false)
  val prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto> = listOf(),
  @param:Schema(description = "Determines behaviour of location groups. True equates to these location groups being included, false equates to them being excluded.", required = true)
  val includeLocationGroupType: Boolean,
  @param:Schema(description = "User Client's for the session template", required = false)
  val clients: List<UserClientDto> = mutableListOf(),
  @param:Schema(description = "Determines behaviour of category groups. True equates to these category groups being included, false equates to them being excluded.", required = true)
  val includeCategoryGroupType: Boolean,
  @param:Schema(description = "Determines behaviour of incentive groups. True equates to these incentive groups being included, false equates to them being excluded.", required = true)
  val includeIncentiveGroupType: Boolean,
  @param:Schema(description = "The type of visit order restriction", example = "PVO", required = true)
  val visitOrderRestriction: SessionTemplateVisitOrderRestrictionType,
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
    includeLocationGroupType = sessionTemplateEntity.includeLocationGroupType,
    includeCategoryGroupType = sessionTemplateEntity.includeCategoryGroupType,
    includeIncentiveGroupType = sessionTemplateEntity.includeIncentiveGroupType,
    clients = sessionTemplateEntity.clients.map { UserClientDto(it.userType, it.active) },
    visitOrderRestriction = sessionTemplateEntity.visitOrderRestriction,
  )
}
