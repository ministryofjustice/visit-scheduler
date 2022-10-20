package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService

@Component
class VisitTask(
  private val visitService: VisitService,
  @Value("\${task.expired-visit.enabled:false}") private val enabled: Boolean
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.expired-visit.cron:0 0/15 * * * ?}")
  @SchedulerLock(
    name = "deleteExpiredVisitsTask",
    lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M"
  )
  fun deleteExpiredReservations() {
    if (!enabled) {
      return
    }

    log.debug("Entered deleteExpiredReservations")
    val expiredApplicationReferences = visitService.findExpiredApplicationReferences()
    log.debug("Expired visits: ${expiredApplicationReferences.count()}")
    if (expiredApplicationReferences.isNotEmpty()) {
      visitService.deleteAllExpiredVisitsByApplicationReference(expiredApplicationReferences)
    }
  }
}
