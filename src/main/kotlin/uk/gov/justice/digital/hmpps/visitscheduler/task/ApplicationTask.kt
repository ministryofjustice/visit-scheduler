package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration.Companion.LOCK_AT_LEAST_FOR
import uk.gov.justice.digital.hmpps.visitscheduler.config.ExpiredApplicationTaskConfiguration.Companion.LOCK_AT_MOST_FOR
import uk.gov.justice.digital.hmpps.visitscheduler.service.ApplicationService

@Component
class ApplicationTask(
  private val applicationService: ApplicationService,
  private val expiredVisitTaskConfiguration: ExpiredApplicationTaskConfiguration,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.expired-visit.cron:0 0/15 * * * ?}")
  @SchedulerLock(
    name = "deleteExpiredVisitsTask",
    lockAtLeastFor = LOCK_AT_LEAST_FOR,
    lockAtMostFor = LOCK_AT_MOST_FOR,
  )
  fun deleteExpiredApplications() {
    if (!expiredVisitTaskConfiguration.expiredApplicationTaskEnabled) {
      return
    }
    applicationService.deleteAllExpiredApplications()
  }
}
