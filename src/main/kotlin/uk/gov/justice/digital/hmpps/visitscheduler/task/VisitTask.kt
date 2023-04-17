package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDateTime
import java.time.LocalTime

@Component
class VisitTask(
  private val visitService: VisitService,
  private val sessionService: SessionService,
  private val prisonConfigService: PrisonConfigService,
  @Value("\${task.expired-visit.enabled:false}") private val enabled: Boolean,
  @Value("\${task.log-non-associations.enabled:false}") private val logNonAssociationsEnabled: Boolean,
  @Value("\${task.log-non-associations.number-of-days-ahead:30}") private val numberOfDaysAhead: Long,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.expired-visit.cron:0 0/15 * * * ?}")
  @SchedulerLock(
    name = "deleteExpiredVisitsTask",
    lockAtLeastFor = "PT5M",
    lockAtMostFor = "PT5M",
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

  @Scheduled(cron = "\${task.log-non-associations.cron:0 0 3 * * ?}")
  fun logNonAssociationVisits() {
    if (!logNonAssociationsEnabled) {
      return
    }

    log.debug("Entered logNonAssociationVisits")
    prisonConfigService.getSupportedPrisons().forEach { prisonCode ->
      for (i in 0..numberOfDaysAhead) {
        val visitFilter = VisitFilter(
          prisonCode = prisonCode,
          visitStatusList = listOf(VisitStatus.BOOKED),
          startDateTime = LocalDateTime.now().plusDays(i).with(LocalTime.MIN),
          endDateTime = LocalDateTime.now().plusDays(i).with(LocalTime.MAX),
        )
        val visits = visitService.findVisitsByFilterPageableDescending(visitFilter)

        visits.forEach {
          val sessions = sessionService.getVisitSessions(it.prisonCode, it.prisonerId, i, i)
          if (sessions.isEmpty()) {
            log.info("Visit with reference - ${it.reference} ,prisoner id - ${it.prisonerId}, prison id - ${it.prisonerId}, start time - ${it.startTimestamp}, end time - ${it.endTimestamp} flagged for check - possible non associations on same day.")
          }
        }
      }
    }
  }
}
