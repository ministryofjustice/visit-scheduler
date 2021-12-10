package uk.gov.justice.digital.hmpps.visitscheduler.helper

import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import java.time.LocalDate
import java.time.LocalTime

fun sessionTemplate(
  id: Long = 123,
  startDate: LocalDate,
  expiryDate: LocalDate? = null,
  closedCapacity: Int = 5,
  openCapacity: Int = 10,
  prisonId: String = "MDI",
  visitRoom: String = "1",
  visitType: VisitType = VisitType.STANDARD_SOCIAL,
  startTime: LocalTime = LocalTime.parse("09:00"),
  endTime: LocalTime = LocalTime.parse("10:00"),
  frequency: String = SessionFrequency.DAILY.name,
  restrictions: String? = null
) = SessionTemplate(
  id = id,
  startDate = startDate,
  expiryDate = expiryDate,
  closedCapacity = closedCapacity,
  openCapacity = openCapacity,
  prisonId = prisonId,
  visitRoom = visitRoom,
  visitType = visitType,
  startTime = startTime,
  endTime = endTime,
  frequency = frequency,
  restrictions = restrictions
)
