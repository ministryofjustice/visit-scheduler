package uk.gov.justice.digital.hmpps.visitscheduler.task

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonConfigService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class VisitTask(
  private val visitService: VisitService,
  private val sessionService: SessionService,
  private val prisonConfigService: PrisonConfigService,
  private val telemetryClient: TelemetryClient,
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

  private fun flagVisit(visit: VisitDto, noticeDays: Long, isRetry: Boolean = false): Boolean {
    var retry = false

    with(visit) {
      log.debug("Started check, visit with reference - ${this.reference}, prisoner id - ${this.prisonerId}, prison code - ${this.prisonCode}, start time - ${this.startTimestamp}, end time - ${this.endTimestamp}")
      var sessions = emptyList<VisitSessionDto>()
      var visitTrackEvent = getFlaggedVisitTrackEvent(visit)

      try {
        sessions = sessionService.getVisitSessions(this.prisonCode, this.prisonerId, noticeDays, noticeDays)
      } catch (e: PrisonerNotInSuppliedPrisonException) {
        visitTrackEvent = handleException(visit, visitTrackEvent, e)
      } catch (e: Exception) {
        // only log this if the visit is being retried
        if (isRetry) {
          visitTrackEvent = handleException(visit, visitTrackEvent, e)
        } else {
          retry = true
        }
      }

      if (sessions.isEmpty() && !retry) {
        trackEvent(visitTrackEvent)
        log.info("Flagged Visit: Visit with reference - ${this.reference}, prisoner id - ${this.prisonerId}, prison code - ${this.prisonCode}, start time - ${this.startTimestamp}, end time - ${this.endTimestamp} flagged for check.")
      }

      log.debug("Finished check, visit with reference - ${this.reference}, prisoner id - ${this.prisonerId}, prison code - ${this.prisonCode}, start time - ${this.startTimestamp}, end time - ${this.endTimestamp}")

      try {
        Thread.sleep(500)
      } catch (e: InterruptedException) {
        log.debug("Flagged Visit: Sleep failed : $e")
      }
    }

    return retry
  }

  private fun handleException(visit: VisitDto, visitTrackEvent: MutableMap<String, String>, e: Exception): MutableMap<String, String> {
    visitTrackEvent["hasException"] = "true"
    if (e is PrisonerNotInSuppliedPrisonException) {
      visitTrackEvent["hasPrisonerMoved"] = "true"
      visitTrackEvent["additionalInformation"] = e.message ?: "Prisoner has moved"
    } else {
      visitTrackEvent["additionalInformation"] = e.message ?: "An exception occurred"
    }
    log.info("Flagged Visit: $e raised for Visit with reference - ${visit.reference} ,prisoner id - ${visit.prisonerId}, prison code - ${visit.prisonCode}, start time - ${visit.startTimestamp}, end time - ${visit.endTimestamp}, error message - ${e.message}")
    return visitTrackEvent
  }

  private fun trackEvent(properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(TelemetryVisitEvents.FLAGGED_VISIT_EVENT.eventName, properties, null)
    } catch (e: RuntimeException) {
      VisitService.LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }

  private fun getFlaggedVisitTrackEvent(visit: VisitDto): MutableMap<String, String> {
    val eventAudit = this.visitService.getLastEventForBooking(visit.reference)

    return mutableMapOf(
      "reference" to visit.reference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prisonCode,
      "visitType" to visit.visitType.name,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitEnd" to visit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to visit.visitStatus.name,
      "createdBy" to eventAudit.actionedBy,
    )
  }
}
