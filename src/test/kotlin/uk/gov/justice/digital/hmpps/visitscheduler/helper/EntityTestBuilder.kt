package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.CreateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.UpdateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.CreateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.UpdateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

fun sessionTemplate(
  name: String = "sessionTemplate_",
  validFromDate: LocalDate,
  validToDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  prisonCode: String = "MDI",
  visitRoom: String = "visitRoom",
  visitType: VisitType = VisitType.SOCIAL,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  permittedSessionLocationGroups: MutableList<SessionLocationGroup> = mutableListOf(),
  permittedSessionCategoryGroups: MutableList<SessionCategoryGroup> = mutableListOf(),
  weeklyFrequency: Int = 1,
  policyNoticeDaysMin: Int = 2,
  policyNoticeDaysMax: Int = 28,
  maxTotalVisitors: Int = 6,
  maxAdultVisitors: Int = 3,
  maxChildVisitors: Int = 3,
  adultAgeYears: Int = 18,
  isActive: Boolean = true,
  includeLocationGroupType: Boolean = true,
): SessionTemplate {
  val prison = Prison(code = prisonCode, active = isActive, policyNoticeDaysMin, policyNoticeDaysMax, maxTotalVisitors, maxAdultVisitors, maxChildVisitors, adultAgeYears)

  return SessionTemplate(
    name = name + dayOfWeek,
    validFromDate = validFromDate,
    validToDate = validToDate,
    closedCapacity = closedCapacity,
    openCapacity = openCapacity,
    prisonId = prison.id,
    prison = prison,
    visitRoom = visitRoom,
    visitType = visitType,
    startTime = startTime,
    endTime = endTime,
    dayOfWeek = dayOfWeek,
    weeklyFrequency = weeklyFrequency,
    active = isActive,
    permittedSessionLocationGroups = permittedSessionLocationGroups,
    permittedSessionCategoryGroups = permittedSessionCategoryGroups,
    includeLocationGroupType = includeLocationGroupType,
  ).also { it.reference = UUID.randomUUID().toString() }
}

fun createCreateSessionTemplateDto(
  name: String = "sessionTemplate_",
  sessionDateRange: SessionDateRangeDto = SessionDateRangeDto(LocalDate.now().minusDays(1), null),
  sessionCapacity: SessionCapacityDto = SessionCapacityDto(closed = 10, open = 5),
  sessionTimeSlot: SessionTimeSlotDto = SessionTimeSlotDto(LocalTime.parse("09:00"), LocalTime.parse("10:00")),
  prisonCode: String = "MDI",
  visitRoom: String = "visitRoom",
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  weeklyFrequency: Int = 1,
  locationGroupReferences: List<String> = listOf(),
  categoryGroupReferences: List<String> = listOf(),
  incentiveLevelGroupReferences: List<String> = listOf(),
  includeLocationGroupType: Boolean = true,
): CreateSessionTemplateDto {
  return CreateSessionTemplateDto(
    name = name + dayOfWeek,
    prisonCode = prisonCode,
    sessionDateRange = sessionDateRange,
    sessionCapacity = sessionCapacity,
    sessionTimeSlot = sessionTimeSlot,
    visitRoom = visitRoom,
    dayOfWeek = dayOfWeek,
    weeklyFrequency = weeklyFrequency,
    locationGroupReferences = locationGroupReferences,
    categoryGroupReferences = categoryGroupReferences,
    incentiveLevelGroupReferences = incentiveLevelGroupReferences,
    includeLocationGroupType = includeLocationGroupType,
  )
}

fun createCreateSessionTemplateDto(
  name: String = "new_sessionTemplate_",
  sessionTemplateDto: SessionTemplateDto,
  dayOfWeek: DayOfWeek? = sessionTemplateDto.dayOfWeek,
  sessionDateRange: SessionDateRangeDto? = sessionTemplateDto.sessionDateRange,
  sessionTimeSlot: SessionTimeSlotDto? = sessionTemplateDto.sessionTimeSlot,
  weeklyFrequency: Int? = sessionTemplateDto.weeklyFrequency,
  includeLocationGroupType: Boolean = true,
): CreateSessionTemplateDto {
  return CreateSessionTemplateDto(
    name = name + sessionTemplateDto.dayOfWeek,
    prisonCode = sessionTemplateDto.prisonCode,
    sessionDateRange = sessionDateRange ?: sessionTemplateDto.sessionDateRange,
    sessionCapacity = sessionTemplateDto.sessionCapacity,
    sessionTimeSlot = sessionTimeSlot ?: sessionTemplateDto.sessionTimeSlot,
    visitRoom = sessionTemplateDto.visitRoom,
    dayOfWeek = dayOfWeek ?: sessionTemplateDto.dayOfWeek,
    weeklyFrequency = weeklyFrequency ?: sessionTemplateDto.weeklyFrequency,
    locationGroupReferences = sessionTemplateDto.permittedLocationGroups.stream().map { it.reference }.toList(),
    categoryGroupReferences = sessionTemplateDto.prisonerCategoryGroups.stream().map { it.reference }.toList(),
    incentiveLevelGroupReferences = sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.reference }.toList(),
    includeLocationGroupType = includeLocationGroupType,
  )
}

fun createUpdateSessionTemplateDto(
  name: String? = "sessionTemplate_",
  sessionDateRange: SessionDateRangeDto? = SessionDateRangeDto(LocalDate.now().minusDays(1), null),
  sessionCapacity: SessionCapacityDto? = SessionCapacityDto(closed = 10, open = 5),
  sessionTimeSlot: SessionTimeSlotDto? = SessionTimeSlotDto(LocalTime.parse("09:00"), LocalTime.parse("10:00")),
  visitRoom: String? = null,
  dayOfWeek: DayOfWeek? = DayOfWeek.FRIDAY,
  weeklyFrequency: Int = 1,
  locationGroupReferences: MutableList<String> = mutableListOf(),
  categoryGroupReferences: MutableList<String> = mutableListOf(),
  incentiveLevelGroupReferences: MutableList<String> = mutableListOf(),
  includeLocationGroupType: Boolean = true,
): UpdateSessionTemplateDto {
  return UpdateSessionTemplateDto(
    name = name + dayOfWeek,
    sessionDateRange = sessionDateRange,
    visitRoom = visitRoom,
    sessionCapacity = sessionCapacity,
    sessionTimeSlot = sessionTimeSlot,
    locationGroupReferences = locationGroupReferences,
    weeklyFrequency = weeklyFrequency,
    categoryGroupReferences = categoryGroupReferences,
    incentiveLevelGroupReferences = incentiveLevelGroupReferences,
  )
}

fun createUpdateSessionTemplateDto(
  sessionTemplateDto: SessionTemplateDto,
  sessionTimeSlot: SessionTimeSlotDto? = null,
  locationGroupReferences: List<String>? = null,
  categoryGroupReferences: List<String>? = null,
  incentiveLevelReferences: List<String>? = null,
): UpdateSessionTemplateDto {
  return UpdateSessionTemplateDto(
    name = sessionTemplateDto.name,
    sessionDateRange = sessionTemplateDto.sessionDateRange,
    sessionCapacity = sessionTemplateDto.sessionCapacity,
    sessionTimeSlot = sessionTimeSlot ?: sessionTemplateDto.sessionTimeSlot,
    locationGroupReferences = locationGroupReferences ?: sessionTemplateDto.permittedLocationGroups.stream().map { it.reference }.toList(),
    weeklyFrequency = sessionTemplateDto.weeklyFrequency,
    categoryGroupReferences = categoryGroupReferences ?: sessionTemplateDto.prisonerCategoryGroups.stream().map { it.reference }.toList(),
    incentiveLevelGroupReferences = incentiveLevelReferences ?: sessionTemplateDto.prisonerIncentiveLevelGroups.stream().map { it.reference }.toList(),
    visitRoom = sessionTemplateDto.visitRoom,
  )
}

fun createCreateLocationGroupDto(
  name: String = "create",
  prisonCode: String = "MDI",
  permittedSessionLocations: MutableList<PermittedSessionLocationDto> = mutableListOf(),
): CreateLocationGroupDto {
  return CreateLocationGroupDto(
    name = name,
    prisonCode = prisonCode,
    locations = permittedSessionLocations,
  )
}

fun createUpdateLocationGroupDto(
  name: String = "update",
  permittedSessionLocations: MutableList<PermittedSessionLocationDto> = mutableListOf(),
): UpdateLocationGroupDto {
  return UpdateLocationGroupDto(
    name = name,
    locations = permittedSessionLocations,
  )
}

fun createPermittedSessionLocationDto(
  levelOneCode: String,
  levelTwoCode: String? = null,
  levelThreeCode: String? = null,
  levelFourCode: String? = null,
): PermittedSessionLocationDto {
  return PermittedSessionLocationDto(
    levelOneCode = levelOneCode,
    levelTwoCode = levelTwoCode,
    levelThreeCode = levelThreeCode,
    levelFourCode = levelFourCode,
  )
}

fun createCategoryGroupDto(
  name: String,
  prisonCode: String,
  vararg type: PrisonerCategoryType,
): CreateCategoryGroupDto {
  return CreateCategoryGroupDto(
    name = name,
    prisonCode = prisonCode,
    categories = type.toList(),
  )
}

fun updateCategoryGroupDto(
  name: String,
  vararg type: PrisonerCategoryType,
): UpdateCategoryGroupDto {
  return UpdateCategoryGroupDto(
    name = name,
    categories = type.toList(),
  )
}

fun createIncentiveGroupDto(
  name: String,
  prisonCode: String,
  vararg type: IncentiveLevel,
): CreateIncentiveGroupDto {
  return CreateIncentiveGroupDto(
    name = name,
    prisonCode = prisonCode,
    incentiveLevels = type.toList(),
  )
}

fun updateIncentiveGroupDto(
  name: String,
  vararg type: IncentiveLevel,
): UpdateIncentiveGroupDto {
  return UpdateIncentiveGroupDto(
    name = name,
    incentiveLevels = type.toList(),
  )
}

fun getSessionDetailsDto(
  createSessionTemplateDto: CreateSessionTemplateDto,
  permittedLocationGroups: List<SessionLocationGroupDto>? = emptyList(),
  prisonerCategoryGroups: List<SessionCategoryGroupDto>? = emptyList(),
  prisonerIncentiveLevelGroups: List<SessionIncentiveLevelGroupDto>? = emptyList(),
): SessionDetailsDto {
  return SessionDetailsDto(
    prisonCode = createSessionTemplateDto.prisonCode,
    sessionTimeSlot = createSessionTemplateDto.sessionTimeSlot,
    sessionDateRange = createSessionTemplateDto.sessionDateRange,
    sessionCapacity = createSessionTemplateDto.sessionCapacity,
    dayOfWeek = createSessionTemplateDto.dayOfWeek,
    weeklyFrequency = createSessionTemplateDto.weeklyFrequency,
    permittedLocationGroups = permittedLocationGroups ?: emptyList(),
    prisonerCategoryGroups = prisonerCategoryGroups ?: emptyList(),
    prisonerIncentiveLevelGroups = prisonerIncentiveLevelGroups ?: emptyList(),
  )
}

fun createSessionLocationGroupDto(allowedSessionLocations: List<AllowedSessionLocationHierarchy>): SessionLocationGroupDto {
  val permittedGroupLocations = mutableListOf<PermittedSessionLocationDto>()
  for (allowedSessionLocation in allowedSessionLocations) {
    val permittedSessionLocation =
      PermittedSessionLocationDto(
        levelOneCode = allowedSessionLocation.levelOneCode,
        levelTwoCode = allowedSessionLocation.levelTwoCode,
        levelThreeCode = allowedSessionLocation.levelThreeCode,
        levelFourCode = allowedSessionLocation.levelFourCode,
      )
    permittedGroupLocations.add(permittedSessionLocation)
  }
  return SessionLocationGroupDto(
    name = "location_group_",
    reference = "location_group_ref",
    locations = permittedGroupLocations,
  )
}
