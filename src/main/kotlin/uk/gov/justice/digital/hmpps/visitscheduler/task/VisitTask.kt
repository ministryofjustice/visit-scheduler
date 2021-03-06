package uk.gov.justice.digital.hmpps.visitscheduler.task

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDateTime

@Component
class VisitTask(
  private val visitService: VisitService,
  @Value("\${task.expired-visit.enabled:false}") private val enabled: Boolean,
  @Value("\${task.expired-visit.validity-minutes:20}") private val expiredPeriod: Long
) {

  @Suppress("KotlinDeprecation")
  @Scheduled(cron = "\${task.expired-visit.cron:0 0/15 * * * ?}")
  fun deleteExpiredReservations() {
    if (!enabled) {
      return
    }

    val expired = visitService.findVisitsByFilterPageableDescending(
      VisitFilter(
        visitStatus = RESERVED,
        modifyTimestamp = LocalDateTime.now().minusMinutes(expiredPeriod)
      )
    ).content

    if (expired.isNotEmpty()) {
      log.debug("Expired visits: ${expired.count()}")
    }

    visitService.deleteAllVisits(expired)
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
