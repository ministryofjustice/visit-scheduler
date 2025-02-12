package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.LocalDate

@Service
class OverbookedSessionsReportService(
  private val telemetryClientService: TelemetryClientService,
  private val visitCountsByDateReportService: VisitCountsByDateReportService,
) {
  fun getOverbookedSessions(reportDate: LocalDate): List<OverbookedSessionsDto> {
    val overBookedVisits = visitCountsByDateReportService.getVisitCountsBySession(reportDate).mapNotNull {
      getOverbookedSessionsDto(it)
    }.filter {
      ((it.openCount > it.sessionCapacity.open) || (it.closedCount > it.sessionCapacity.closed))
    }
    return overBookedVisits.sortedBy { it.sessionDate }.sortedBy { it.sessionTimeSlot.startTime }
  }

  fun sendTelemetryEvent(overbookedSessions: List<OverbookedSessionsDto>) {
    overbookedSessions.forEach {
      telemetryClientService.trackOverbookedSessionsEvent(it)
    }
  }

  private fun getOverbookedSessionsDto(sessionVisitCountsDto: SessionVisitCountsDto): OverbookedSessionsDto? = if (
    !sessionVisitCountsDto.hasSessionsOnDate ||
    sessionVisitCountsDto.isBlockedDate ||
    sessionVisitCountsDto.sessionTimeSlot == null ||
    sessionVisitCountsDto.sessionCapacity == null
  ) {
    null
  } else {
    with(sessionVisitCountsDto) {
      OverbookedSessionsDto(
        sessionDate = reportDate,
        prisonCode = prisonCode,
        sessionTimeSlot = sessionTimeSlot!!,
        sessionCapacity = sessionCapacity!!,
        openCount = openBookedCount ?: 0,
        closedCount = closedBookedCount ?: 0,
      )
    }
  }
}
