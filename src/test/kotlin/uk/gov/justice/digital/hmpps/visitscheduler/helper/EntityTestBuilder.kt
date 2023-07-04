package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.CreateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.UpdateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.CreateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.UpdateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
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
  isActive: Boolean = true,
): SessionTemplate {
  val prison = Prison(code = prisonCode, active = true)

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
  ).also { it.reference = UUID.randomUUID().toString() }
}

fun createSessionTemplateDto(
  name: String = "sessionTemplate_",
  sessionDateRangeDto: SessionDateRangeDto = SessionDateRangeDto(LocalDate.now().minusDays(1), null),
  sessionCapacity: SessionCapacityDto = SessionCapacityDto(closed = 10, open = 5),
  sessionTimeSlotDto: SessionTimeSlotDto = SessionTimeSlotDto(LocalTime.parse("09:00"), LocalTime.parse("10:00")),
  prisonCode: String = "MDI",
  visitRoom: String = "visitRoom",
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  weeklyFrequency: Int = 1,
  locationGroupReferences: MutableList<String> = mutableListOf(),
  categoryGroupReferences: MutableList<String> = mutableListOf(),
  incentiveLevelGroupReferences: MutableList<String> = mutableListOf(),
): CreateSessionTemplateDto {
  return CreateSessionTemplateDto(
    name = name + dayOfWeek,
    prisonCode = prisonCode,
    sessionDateRange = sessionDateRangeDto,
    sessionCapacity = sessionCapacity,
    sessionTimeSlot = sessionTimeSlotDto,
    visitRoom = visitRoom,
    dayOfWeek = dayOfWeek,
    weeklyFrequency = weeklyFrequency,
    locationGroupReferences = locationGroupReferences,
    categoryGroupReferences = categoryGroupReferences,
    incentiveLevelGroupReferences = incentiveLevelGroupReferences,
  )
}

fun createUpdateSessionTemplateDto(
  name: String? = "sessionTemplate_",
  sessionDateRangeDto: SessionDateRangeDto? = SessionDateRangeDto(LocalDate.now().minusDays(1), null),
  sessionCapacity: SessionCapacityDto? = SessionCapacityDto(closed = 10, open = 5),
  sessionTimeSlotDto: SessionTimeSlotDto? = SessionTimeSlotDto(LocalTime.parse("09:00"), LocalTime.parse("10:00")),
  dayOfWeek: DayOfWeek? = DayOfWeek.FRIDAY,
  weeklyFrequency: Int = 1,
  locationGroupReferences: MutableList<String> = mutableListOf(),
  categoryGroupReferences: MutableList<String> = mutableListOf(),
  incentiveLevelGroupReferences: MutableList<String> = mutableListOf(),
): UpdateSessionTemplateDto {
  return UpdateSessionTemplateDto(
    name = name + dayOfWeek,
    sessionDateRange = sessionDateRangeDto,
    sessionCapacity = sessionCapacity,
    sessionTimeSlot = sessionTimeSlotDto,
    locationGroupReferences = locationGroupReferences,
    weeklyFrequency = weeklyFrequency,
    categoryGroupReferences = categoryGroupReferences,
    incentiveLevelGroupReferences = incentiveLevelGroupReferences,
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
