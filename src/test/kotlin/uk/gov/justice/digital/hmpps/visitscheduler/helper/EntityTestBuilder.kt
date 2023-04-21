package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
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
  capacityGroup: String = "1",
  visitType: VisitType = VisitType.SOCIAL,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  permittedSessionLocationGroups: MutableList<SessionLocationGroup> = mutableListOf(),
  permittedSessionCategoryGroups: MutableList<SessionCategoryGroup> = mutableListOf(),
  biWeekly: Boolean = false,
  enhanced: Boolean = true,
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
    capacityGroup = capacityGroup,
    visitType = visitType,
    startTime = startTime,
    endTime = endTime,
    dayOfWeek = dayOfWeek,
    permittedSessionLocationGroups = permittedSessionLocationGroups,
    permittedSessionCategoryGroups = permittedSessionCategoryGroups,
    biWeekly = biWeekly,
    enhanced = enhanced,
  ).also { it.reference = UUID.randomUUID().toString() }
}

fun createSessionTemplateDto(
  name: String = "sessionTemplate_",
  validFromDate: LocalDate = LocalDate.now().minusDays(1),
  validToDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  prisonCode: String = "MDI",
  capacityGroup: String = "1",
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  locationGroupReferences: MutableList<String> = mutableListOf(),
  biWeekly: Boolean = false,
  enhanced: Boolean = true,
  categoryGroupReferences: MutableList<String> = mutableListOf(),
): CreateSessionTemplateDto {
  return CreateSessionTemplateDto(
    name = name + dayOfWeek,
    prisonCode = prisonCode,
    validFromDate = validFromDate,
    validToDate = validToDate,
    closedCapacity = closedCapacity,
    openCapacity = openCapacity,
    capacityGroup = capacityGroup,
    startTime = startTime,
    endTime = endTime,
    dayOfWeek = dayOfWeek,
    locationGroupReferences = locationGroupReferences,
    biWeekly = biWeekly,
    enhanced = enhanced,
    categoryGroupReferences = categoryGroupReferences,
  )
}

fun createUpdateSessionTemplateDto(
  name: String = "sessionTemplate_",
  validFromDate: LocalDate = LocalDate.now().minusDays(1),
  validToDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  locationGroupReferences: MutableList<String> = mutableListOf(),
  biWeekly: Boolean = false,
  enhanced: Boolean = true,
  categoryGroupReferences: MutableList<String> = mutableListOf(),
): UpdateSessionTemplateDto {
  return UpdateSessionTemplateDto(
    name = name + dayOfWeek,
    validFromDate = validFromDate,
    validToDate = validToDate,
    closedCapacity = closedCapacity,
    openCapacity = openCapacity,
    startTime = startTime,
    endTime = endTime,
    locationGroupReferences = locationGroupReferences,
    biWeekly = biWeekly,
    enhanced = enhanced,
    categoryGroupReferences = categoryGroupReferences,
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

fun updateLocationGroupDto(
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
