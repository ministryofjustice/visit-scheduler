package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.config.FlagVisitTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDate

@Component
class VisitTask(
  private val visitService: VisitService,
  private val sessionService: SessionService,
  private val prisonsService: PrisonsService,
  private val flagVisitTaskConfiguration: FlagVisitTaskConfiguration,
  private val telemetryClientService: TelemetryClientService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "\${task.log-non-associations.cron:0 0 3 * * ?}")
  @SchedulerLock(
    name = "flagVisitsTask",
    lockAtLeastFor = FlagVisitTaskConfiguration.LOCK_AT_LEAST_FOR,
    lockAtMostFor = FlagVisitTaskConfiguration.LOCK_AT_MOST_FOR,
  )
  fun flagVisits() {
    if (!flagVisitTaskConfiguration.flagVisitsEnabled) {
      return
    }

    log.debug("Started flagVisits task.")
    prisonsService.getPrisonCodes().forEach { prisonCode ->
      for (i in 0..flagVisitTaskConfiguration.numberOfDaysAhead) {
        val visitDate = LocalDate.now().plusDays(i.toLong())

        val visits = visitService.getBookedVisitsForDate(
          prisonCode = prisonCode,
          visitDate,
        )

        val retryVisits = mutableListOf<VisitDto>()

        visits.forEach {
          val retry = flagVisit(it, i)
          if (retry) {
            retryVisits.add(it)
          }
        }

        // finally run the retry visits loop once
        retryVisits.forEach {
          flagVisit(it, i, true)
        }
      }
    }

    log.debug("Finished flagVisits task.")
  }

  private fun flagVisit(visit: VisitDto, noticeDays: Int, isRetry: Boolean = false): Boolean {
    var retry = false

    log.debug("Started check, visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {}", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp)
    var sessions = emptyList<VisitSessionDto>()

    var exception: Exception? = null
    try {
      sessions = sessionService.getVisitSessions(prisonCode = visit.prisonCode, prisonerId = visit.prisonerId, minOverride = noticeDays, maxOverride = noticeDays)
    } catch (e: PrisonerNotInSuppliedPrisonException) {
      exception = e
    } catch (e: Exception) {
      // only log this if the visit is being retried
      if (isRetry) {
        exception = e
      } else {
        retry = true
      }
    }

    if (sessions.isEmpty() && !retry) {
      trackEvent(visit, exception)
      log.info("Flagged Visit: Visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {} flagged for check.", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp)
    }

    log.debug("Finished check, visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {}", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp)

    try {
      Thread.sleep(FlagVisitTaskConfiguration.THREAD_SLEEP_TIME_IN_MILLISECONDS)
    } catch (e: InterruptedException) {
      log.debug("Flagged Visit: Sleep failed : {}", e.toString())
    }

    return retry
  }

  private fun trackEvent(visit: VisitDto, exception: Exception?) {
    try {
      telemetryClientService.trackFlaggedVisitEvent(visit, exception)
    } catch (e: RuntimeException) {
      VisitService.LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }
}
