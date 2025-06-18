package uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import java.time.DayOfWeek

data class SessionDetailsDto(
  @Schema(description = "prisonId", example = "MDI", required = true)
  @field:NotBlank
  val prisonCode: String,

  @Schema(description = "The time slot of the generated visit session(s)", required = true)
  val sessionTimeSlot: SessionTimeSlotDto,

  @Schema(description = "Validity period for the session template", required = true)
  val sessionDateRange: SessionDateRangeDto,

  @Schema(description = "number of weeks until the weekly day is repeated", example = "1", required = true)
  val weeklyFrequency: Int,

  @Schema(description = "session capacity", required = true)
  val sessionCapacity: SessionCapacityDto,

  @Schema(description = "day of week for visit", example = "MONDAY", required = false)
  val dayOfWeek: DayOfWeek,

  @Schema(description = "Determines behaviour of location groups. True equates to these location groups being included, false equates to them being excluded.", required = true)
  val includeLocationGroupType: Boolean,

  @Schema(description = "list of permitted session location groups", required = false)
  val permittedLocationGroups: List<SessionLocationGroupDto> = listOf(),

  @Schema(description = "Determines behaviour of category groups. True equates to these category groups being included, false equates to them being excluded.", required = true)
  val includeCategoryGroupType: Boolean,

  @Schema(description = "list of permitted prisoner category groups", required = false)
  val prisonerCategoryGroups: List<SessionCategoryGroupDto> = listOf(),

  @Schema(description = "Determines behaviour of incentive groups. True equates to these incentive groups being included, false equates to them being excluded.", required = true)
  val includeIncentiveGroupType: Boolean,

  @Schema(description = "list of permitted incentive level groups", required = false)
  val prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto> = listOf(),
) {
  constructor(sessionTemplateDto: SessionTemplateDto) :
    this(
      prisonCode = sessionTemplateDto.prisonCode,
      sessionTimeSlot = sessionTemplateDto.sessionTimeSlot,
      sessionCapacity = sessionTemplateDto.sessionCapacity,
      sessionDateRange = sessionTemplateDto.sessionDateRange,
      weeklyFrequency = sessionTemplateDto.weeklyFrequency,
      dayOfWeek = sessionTemplateDto.dayOfWeek,
      includeLocationGroupType = sessionTemplateDto.includeLocationGroupType,
      permittedLocationGroups = sessionTemplateDto.permittedLocationGroups,
      includeCategoryGroupType = sessionTemplateDto.includeCategoryGroupType,
      prisonerCategoryGroups = sessionTemplateDto.prisonerCategoryGroups,
      includeIncentiveGroupType = sessionTemplateDto.includeIncentiveGroupType,
      prisonerIncentiveLevelGroups = sessionTemplateDto.prisonerIncentiveLevelGroups,
    )
}
