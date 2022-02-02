package uk.gov.justice.digital.hmpps.visitscheduler.task

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.data.filter.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitSchedulerService
import java.time.LocalDateTime

@Component
class VisitTask(
  private val visitSchedulerService: VisitSchedulerService,
  @Value("\${task.expired-visit.enabled:false}") private val enabled: Boolean,
  @Value("\${task.expired-visit.validity-minutes:20}") private val expiredPeriod: Long
) {

  @Scheduled(cron = "\${task.expired-visit.cron:0 0/15 * * * ?}")
  fun deleteExpiredReservations() {
    if (!enabled) {
      return
    }

    val expired = visitSchedulerService.findVisitsByFilter(
      VisitFilter(
        status = VisitStatus.RESERVED,
        modifyTimestamp = LocalDateTime.now().minusMinutes(expiredPeriod)
      )
    )

    if (expired.isNotEmpty()) {
      log.debug("Expired visits: ${expired.count()}")
    }

    expired.forEach {
      visitSchedulerService.deleteVisit(it.id)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
