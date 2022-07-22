package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

fun sessionTemplate(
  id: Long = 123,
  validFromDate: LocalDate,
  validToDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  prisonId: String = "MDI",
  visitRoom: String = "1",
  visitType: VisitType = VisitType.SOCIAL,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  dayOfWeek: DayOfWeek = DayOfWeek.FRIDAY
) = SessionTemplate(
  id = id,
  validFromDate = validFromDate,
  validToDate = validToDate,
  closedCapacity = closedCapacity,
  openCapacity = openCapacity,
  prisonId = prisonId,
  visitRoom = visitRoom,
  visitType = visitType,
  startTime = startTime,
  endTime = endTime,
  dayOfWeek = dayOfWeek
)
