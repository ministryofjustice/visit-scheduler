package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration.Companion.LOCK_AT_LEAST_FOR
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration.Companion.LOCK_AT_MOST_FOR
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitRequestsService

@Component
class AutoRejectVisitRequestsTask(private val visitRequestsService: VisitRequestsService) {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  // Every day at 8:00 PM
  @Scheduled(cron = "\${task.requests.auto-reject.cron:0 0 20 * * *}")
  @SchedulerLock(
    name = "autoRejectRequestedVisitsTask",
    lockAtLeastFor = LOCK_AT_LEAST_FOR,
    lockAtMostFor = LOCK_AT_MOST_FOR,
  )
  fun autoRejectRequestVisits() {
    LOG.info("Starting auto reject request visits task")
    visitRequestsService.autoRejectRequestVisitsAtMinimumBookingWindow()
  }
}
