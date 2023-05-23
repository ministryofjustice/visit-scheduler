package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
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
  @Value("\${task.log-non-associations.enabled:false}") private val flagVisitsEnabled: Boolean,
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
  @SchedulerLock(
    name = "flagVisitsTask",
    lockAtLeastFor = "PT60M",
    lockAtMostFor = "PT60M",
  )
  fun flagVisits() {
    if (!flagVisitsEnabled) {
      return
    }

    log.debug("Started flagVisits task.")
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
          var sessions = emptyList<VisitSessionDto>()
          try {
            sessions = sessionService.getVisitSessions(it.prisonCode, it.prisonerId, i, i)
          } catch (e: Exception) {
            log.info("Flagged Visit: Exception raised for Visit with reference - ${it.reference} ,prisoner id - ${it.prisonerId}, prison code - ${it.prisonCode}, start time - ${it.startTimestamp}, end time - ${it.endTimestamp}, error message - ${e.message}")
          }
          if (sessions.isEmpty()) {
            log.info("Flagged Visit: Visit with reference - ${it.reference} ,prisoner id - ${it.prisonerId}, prison code - ${it.prisonCode}, start time - ${it.startTimestamp}, end time - ${it.endTimestamp} flagged for check.")
          }

          try {
            Thread.sleep(500)
          } catch (e: InterruptedException) {
            log.error("Flagged Visit: Sleep failed : $e")
          }
        }
      }
    }

    log.debug("Finished flagVisits task.")
  }
}
