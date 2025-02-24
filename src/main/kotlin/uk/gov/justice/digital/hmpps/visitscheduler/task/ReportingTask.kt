package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.config.ReportingTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.reporting.VisitsReportingService
import java.time.LocalDate

@Component
class ReportingTask(
  private val visitReportingService: VisitsReportingService,
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
  fun getVisitCountsReportByDay(): Map<LocalDate, List<SessionVisitCountsDto>> = if (!reportingTaskConfiguration.visitCountsReportingEnabled) {
    LOG.info("Reporting task for visit counts not enabled")
    emptyMap()
  } else {
    visitReportingService.getVisitCountsReportByDay()
  }

  @Scheduled(cron = "\${task.overbooked-sessions-report.cron:0 30 1 * * ?}")
  @SchedulerLock(
    name = "Overbooked sessions report",
    lockAtLeastFor = ReportingTaskConfiguration.LOCK_AT_LEAST_FOR,
    lockAtMostFor = ReportingTaskConfiguration.LOCK_AT_MOST_FOR,
  )
  fun reportOverbookedSessions(): List<OverbookedSessionsDto> = if (!reportingTaskConfiguration.overbookedSessionsReportingEnabled) {
    LOG.info("Reporting task for overbooked sessions not enabled")
    emptyList()
  } else {
    visitReportingService.getOverbookedSessions(fromDate = LocalDate.now())
  }
}
