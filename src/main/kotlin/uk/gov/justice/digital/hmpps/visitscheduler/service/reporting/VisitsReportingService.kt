package uk.gov.justice.digital.hmpps.visitscheduler.service.reporting

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VSIPReporting
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VSIPReportingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask.Companion.LOG
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
class VisitsReportingService(
  private val visitCountsByDateReportService: VisitCountsByDateReportService,
  private val vsipReportingRepository: VSIPReportingRepository,
) {
  fun getVisitCountsReportByDay(): Map<LocalDate, List<SessionVisitCountsDto>> {
    val sessionsReports = mutableMapOf<LocalDate, List<SessionVisitCountsDto>>()

    val reportDate = getNextReportDate()
    if (reportDate != null) {
      val maxReportDate = LocalDate.now()
      reportDate.datesUntil(maxReportDate).forEach { forDate ->
        val sessionReport = visitCountsByDateReportService.getVisitCountsReportForDate(forDate)
        sessionsReports[reportDate] = sessionReport
        visitCountsByDateReportService.sendTelemetryEvent(sessionReport)
        updateLastRunReportDate(forDate)
      }
    } else {
      LOG.info("No report date configured for {} report", VSIPReport.VISIT_COUNTS_BY_DAY)
    }
    return sessionsReports
  }

  private fun getNextReportDate(): LocalDate? {
    val reportDetails = vsipReportingRepository.findById(VSIPReport.VISIT_COUNTS_BY_DAY)
    return reportDetails.getOrNull()?.lastReportDate?.plusDays(1)
  }

  private fun updateLastRunReportDate(reportDate: LocalDate) {
    vsipReportingRepository.save(VSIPReporting(VSIPReport.VISIT_COUNTS_BY_DAY, reportDate))
  }
}
