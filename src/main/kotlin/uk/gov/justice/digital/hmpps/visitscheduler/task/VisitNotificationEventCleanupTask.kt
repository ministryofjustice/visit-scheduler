package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService

@Component
class VisitNotificationEventCleanupTask(val visitNotificationEventService: VisitNotificationEventService) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  // TODO: Cron timing variable
  @Scheduled(cron = "\${task.visit-notification-events.cron:0 30 3 * * ?}")
  @SchedulerLock(
    name = "Visit notification events - clean up events in the past",
    lockAtLeastFor = "PT5M",
    lockAtMostFor = "PT5M",
  )
  fun deleteOutdatedVisitNotificationEvents() {
    LOG.info("Started deleteOutdatedVisitNotificationEvents task")

    val amountDeleted = visitNotificationEventService.deleteExpiredNotificationEvents()

    LOG.info("Deleted $amountDeleted visit notification events - visit in the past")
  }
}
