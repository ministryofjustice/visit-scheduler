package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

fun sessionTemplate(
  name: String = "sessionTemplate_",
  validFromDate: LocalDate,
  validToDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  prisonCode: String = "MDI",
  visitRoom: String = "1",
  visitType: VisitType = VisitType.SOCIAL,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
  permittedSessionGroups: MutableList<SessionLocationGroup> = mutableListOf(),
  biWeekly: Boolean = false,
): SessionTemplate {

  val prison = Prison(id = 0, code = prisonCode, active = true)

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
    permittedSessionGroups = permittedSessionGroups,
    biWeekly = biWeekly
  )
}
