package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.config.ReportingTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VSIPReporting
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VSIPReportingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitscheduler.service.reporting.VisitsReportingService
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Component
class ReportingTask(
  private val visitReportingService: VisitsReportingService,
  private val telemetryClientService: TelemetryClientService,
  private val vsipReportingRepository: VSIPReportingRepository,
  private val reportingTaskConfiguration: ReportingTaskConfiguration,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.visit-counts-report.cron:0 0 1 * * ?}")
  @SchedulerLock(
    name = "VSIP Reporting - sessions and book count",
    lockAtLeastFor = ReportingTaskConfiguration.LOCK_AT_LEAST_FOR,
    lockAtMostFor = ReportingTaskConfiguration.LOCK_AT_MOST_FOR,
  )
  fun getVisitCountsReportByDay(): Map<LocalDate, List<SessionVisitCountsDto>> {
    val sessionsReports = mutableMapOf<LocalDate, List<SessionVisitCountsDto>>()

    if (!reportingTaskConfiguration.reportingEnabled) {
      LOG.info("Reporting task for visit counts not enabled")
    } else {
      val reportDate = getNextReportDate()
      if (reportDate != null) {
        val maxReportDate = LocalDate.now()
        reportDate.datesUntil(maxReportDate).forEach { forDate ->
          val sessionReport = getVisitCountsReportForDate(forDate)
          sessionsReports[reportDate] = sessionReport
          sendTelemetryEvent(sessionReport)
          updateLastRunReportDate(forDate)
        }
      } else {
        LOG.info("No report date configured for {} report", VSIPReport.VISIT_COUNTS_BY_DAY)
      }
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

  private fun getVisitCountsReportForDate(reportDate: LocalDate): List<SessionVisitCountsDto> {
    val today = LocalDate.now()
    return if (reportDate == today || reportDate.isAfter(today)) {
      LOG.info("Report date {} is not in the past.", reportDate)
      emptyList()
    } else {
      visitReportingService.getVisitCountsBySession(reportDate)
    }
  }

  private fun sendTelemetryEvent(sessionReports: List<SessionVisitCountsDto>) {
    sessionReports.forEach { sessionReport ->
      val event = createVisitCountTelemetryEventMap(sessionReport)
      telemetryClientService.trackEvent(TelemetryVisitEvents.VISIT_COUNTS_REPORT, event)
    }
  }

  fun createVisitCountTelemetryEventMap(
    sessionReport: SessionVisitCountsDto,
  ): Map<String, String> {
    val reportEvent = mutableMapOf<String, String>()
    reportEvent["reportDate"] = telemetryClientService.formatDateToString(sessionReport.reportDate)
    reportEvent["prisonCode"] = sessionReport.prisonCode
    reportEvent["blockedDate"] = sessionReport.isBlockedDate.toString()
    reportEvent["hasSessions"] = sessionReport.hasSessionsOnDate.toString()

    sessionReport.sessionTimeSlot?.let {
      reportEvent["sessionStart"] = telemetryClientService.formatTimeToString(it.startTime)
      reportEvent["sessionEnd"] = telemetryClientService.formatTimeToString(it.endTime)
    }
    sessionReport.sessionCapacity?.let {
      reportEvent["openCapacity"] = it.open.toString()
      reportEvent["closedCapacity"] = it.closed.toString()
    }
    sessionReport.visitType?.let {
      reportEvent["visitType"] = it.toString()
    }
    sessionReport.openBookedCount?.let {
      reportEvent["openBooked"] = it.toString()
    }
    sessionReport.closedBookedCount?.let {
      reportEvent["closedBooked"] = it.toString()
    }
    sessionReport.openCancelledCount?.let {
      reportEvent["openCancelled"] = it.toString()
    }
    sessionReport.closedCancelledCount?.let {
      reportEvent["closedCancelled"] = it.toString()
    }

    return reportEvent.toMap()
  }
}
