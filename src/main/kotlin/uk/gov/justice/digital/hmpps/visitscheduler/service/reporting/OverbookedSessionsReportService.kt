package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsByDateDto
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

  private fun getOverbookedSessionsDto(sessionVisitCountsByDateDto: SessionVisitCountsByDateDto): OverbookedSessionsDto? = if (
    !sessionVisitCountsByDateDto.hasSessionsOnDate ||
    sessionVisitCountsByDateDto.isBlockedDate ||
    sessionVisitCountsByDateDto.visitCountBySession == null
  ) {
    null
  } else {
    with(sessionVisitCountsByDateDto) {
      OverbookedSessionsDto(
        sessionDate = reportDate,
        prisonCode = prisonCode,
        sessionTimeSlot = visitCountBySession?.sessionTimeSlot!!,
        sessionCapacity = visitCountBySession?.sessionCapacity!!,
        openCount = visitCountBySession?.openBookedCount ?: 0,
        closedCount = visitCountBySession?.closedBookedCount ?: 0,
      )
    }
  }
}
