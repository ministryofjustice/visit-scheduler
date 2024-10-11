package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport.VISIT_COUNTS_BY_DAY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VSIPReporting
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VSIPReportingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask.Companion.LOG
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
class VisitsReportingService(
  private val visitCountsByDateReportService: VisitCountsByDateReportService,
  private val overbookedVisitsReportService: OverbookedSessionsReportService,
  private val vsipReportingRepository: VSIPReportingRepository,
  @Value("\${task.overbooked-sessions-report.futureDays:7}") private val maxDays: Long,
) {
  fun getVisitCountsReportByDay(): Map<LocalDate, List<SessionVisitCountsDto>> {
    val sessionsReports = mutableMapOf<LocalDate, List<SessionVisitCountsDto>>()

    val reportDate = getNextReportDate(VISIT_COUNTS_BY_DAY)
    if (reportDate != null) {
      val maxReportDate = LocalDate.now()
      reportDate.datesUntil(maxReportDate).forEach { forDate ->
        val sessionReport = visitCountsByDateReportService.getVisitCountsReportForDate(forDate)
        sessionsReports[reportDate] = sessionReport
        visitCountsByDateReportService.sendTelemetryEvent(sessionReport)
        updateLastRunReportDate(VISIT_COUNTS_BY_DAY, forDate)
      }
    } else {
      LOG.info("No report date configured for {} report", VISIT_COUNTS_BY_DAY)
    }
    return sessionsReports
  }

  fun getOverbookedSessions(fromDate: LocalDate): List<OverbookedSessionsDto> {
    val overbookedSessionsReport = mutableListOf<OverbookedSessionsDto>()

    val maxReportDate = fromDate.plusDays(maxDays)

    fromDate.datesUntil(maxReportDate).forEach { forDate ->
      val overbookedSessionsForDate = overbookedVisitsReportService.getOverbookedSessions(forDate).also {
        overbookedVisitsReportService.sendTelemetryEvent(it)
      }

      overbookedSessionsReport.addAll(overbookedSessionsForDate)
    }

    return overbookedSessionsReport
  }

  private fun getNextReportDate(vsipReport: VSIPReport): LocalDate? {
    val reportDetails = vsipReportingRepository.findById(vsipReport)
    return reportDetails.getOrNull()?.lastReportDate?.plusDays(1)
  }

  private fun updateLastRunReportDate(vsipReport: VSIPReport, reportDate: LocalDate) {
    vsipReportingRepository.save(VSIPReporting(vsipReport, reportDate))
  }
}
