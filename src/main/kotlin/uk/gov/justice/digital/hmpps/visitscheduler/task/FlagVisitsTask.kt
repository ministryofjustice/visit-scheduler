package uk.gov.justice.digital.hmpps.visitscheduler.task

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient
import uk.gov.justice.digital.hmpps.visitscheduler.config.FlagVisitTaskConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.VisitSessionDto
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SessionService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import java.time.LocalDate

@Component
class FlagVisitsTask(
  private val visitService: VisitService,
  private val sessionService: SessionService,
  private val prisonsService: PrisonsService,
  private val flagVisitTaskConfiguration: FlagVisitTaskConfiguration,
  private val telemetryClientService: TelemetryClientService,
  private val visitNotificationEventService: VisitNotificationEventService,
  private val prisonerContactRegistryClient: PrisonerContactRegistryClient,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
    private const val DEFAULT_VISIT_FLAG_REASON = "session not suitable"
  }

  @Scheduled(cron = "\${task.flag-visits.cron:0 0 3 * * ?}")
  @SchedulerLock(
    name = "flagVisitsTask",
    lockAtLeastFor = FlagVisitTaskConfiguration.LOCK_AT_LEAST_FOR,
    lockAtMostFor = FlagVisitTaskConfiguration.LOCK_AT_MOST_FOR,
  )
  fun flagVisits() {
    LOG.info("Started flagVisits task.")
    if (!flagVisitTaskConfiguration.flagVisitsEnabled) {
      LOG.info("flagVisits task disabled, exiting task.")
      return
    }

    prisonsService.getPrisonCodes().forEach { prisonCode ->
      flagPrisonVisits(prisonCode)

      // flag any visits with visitors below allowed age on an age-restricted session
      flagAgeRestrictedVisits(prisonCode)
    }

    LOG.info("Finished flagVisits task.")
  }

  private fun flagPrisonVisits(prisonCode: String) {
    LOG.info("Flagging visits for prison {}.", prisonCode)
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

      // finally, run the retry visits loop once
      retryVisits.forEach {
        flagVisit(it, i, true)
      }
    }
  }

  private fun flagVisit(visit: VisitDto, noticeDays: Int, isRetry: Boolean = false): Boolean {
    var markForRetry = false
    var reason: String? = null
    var sessions = emptyList<VisitSessionDto>()

    LOG.debug("Started check, visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {}", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp)
    val notifications = getVisitNotifications(visit.reference).toSet()
    val hasNotifications = notifications.isNotEmpty()

    if (hasNotifications) {
      reason = notifications.joinToString(", ") { it.description }
    } else {
      try {
        sessions = sessionService.getAllVisitSessions(prisonCode = visit.prisonCode, prisonerId = visit.prisonerId, minOverride = noticeDays, maxOverride = noticeDays, userType = UserType.STAFF).filter { it.sessionTemplateReference == visit.sessionTemplateReference }
      } catch (e: Exception) {
        if (isRetry) {
          // only log this if the visit is being retried
          LOG.info("Exception thrown when retrieving visit sessions for the following parameters - prison code - {}, prisonerId - {}, minOverride - {}, maxOverride - {}, exception - {}, {}", visit.prisonCode, visit.prisonerId, noticeDays, noticeDays, e.message, e.toString())
        } else {
          // if isRetry is false it would mean that the visit has not been retried before so set markForRetry to true
          // this is to ensure for exceptions other than PrisonerNotInSuppliedPrisonException the visit is retried once.
          markForRetry = true
        }
      }
    }

    if (hasNotifications || (sessions.isEmpty() && !markForRetry)) {
      trackEvent(visit, reason ?: DEFAULT_VISIT_FLAG_REASON)
      LOG.info("Flagged Visit: Visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {} flagged for check , hasNotifications is {} and / or session not available - {}.", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp, hasNotifications, sessions.isEmpty())
    }

    LOG.debug("Finished check, visit with reference - {}, prisoner id - {}, prison code - {}, start time - {}, end time - {}", visit.reference, visit.prisonerId, visit.prisonCode, visit.startTimestamp, visit.endTimestamp)

    try {
      Thread.sleep(FlagVisitTaskConfiguration.THREAD_SLEEP_TIME_IN_MILLISECONDS)
    } catch (e: InterruptedException) {
      LOG.debug("Flagged Visit: Sleep failed : {}", e.toString())
    }

    return markForRetry
  }

  private fun flagAgeRestrictedVisits(prisonCode: String) {
    LOG.debug("Flagging visits for age restricted sessions")
    val ageRestrictedSessionTemplates = sessionService.getAgeRestrictedSessionTemplates(prisonCode)
    val reportDates = getReportDates()
    if (ageRestrictedSessionTemplates.isEmpty()) {
      LOG.debug("No age restricted session templates found for prison {}", prisonCode)
      return
    } else {
      ageRestrictedSessionTemplates.forEach { ageRestrictedSessionTemplate ->
        LOG.debug("Session template {} is age restricted, allowed age - {}.", ageRestrictedSessionTemplate.reference, ageRestrictedSessionTemplate.ageRestriction)
        val ageRestrictedSessionDates = reportDates.filter { it.dayOfWeek == ageRestrictedSessionTemplate.dayOfWeek }
        ageRestrictedSessionDates.forEach { visitDate ->
          LOG.debug("Flagging visits for Session template {} and visit date - {}.", ageRestrictedSessionTemplate.reference, visitDate)
          flagAgeRestrictedVisitorsOnSession(ageRestrictedSessionTemplate, visitDate)
        }
      }
    }
  }

  private fun flagAgeRestrictedVisitorsOnSession(ageRestrictedSessionTemplate: SessionTemplateDto, visitDate: LocalDate) {
    val visits = visitService.getBookedVisitsBySessionForDate(ageRestrictedSessionTemplate.reference, visitDate)

    // go through all visits for the date and flag any visits that have age-restricted visitors
    visits.forEach { visit ->
      if (hasAgeRestrictedVisitors(visit, ageRestrictedSessionTemplate)) {
        LOG.debug("Flagging visit - {} as it has age restricted visitors.", visit.reference)
        trackEvent(visit, "Age restricted visit")
      }
    }
  }

  private fun hasAgeRestrictedVisitors(visit: VisitDto, ageRestrictedSessionTemplate: SessionTemplateDto): Boolean {
    val visitorsWithDOBDetails = getVisitorDOBs(visit)
    val visitDate = visit.startTimestamp.toLocalDate()
    val allowedAge = ageRestrictedSessionTemplate.ageRestriction
    return (visitorsWithDOBDetails != null && isAnyVisitorBelowAllowedAgeOnVisitDate(visitDate, visitorsWithDOBDetails, allowedAge))
  }

  private fun getVisitorDOBs(visit: VisitDto): Map<Long, LocalDate?>? = try {
    prisonerContactRegistryClient.searchContacts(contactIds = visit.visitors.map { it.nomisPersonId }, withRestrictions = false)?.associate { it.contactId to it.dateOfBirth }
  } catch (e: RuntimeException) {
    LOG.error("Error occurred in call to prisoner contact registry client to get contact details", e)
    emptyMap()
  }

  private fun isAnyVisitorBelowAllowedAgeOnVisitDate(visitDate: LocalDate, visitors: Map<Long, LocalDate?>, allowedAge: Int): Boolean = visitors.any { (_, dob) -> isVisitorBelowAllowedAge(dob = dob, visitDate = visitDate, allowedAge = allowedAge) }

  private fun trackEvent(visit: VisitDto, reason: String) {
    try {
      telemetryClientService.trackFlaggedVisitEvent(visit, reason)
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }

  private fun getReportDates(): List<LocalDate> {
    val fromDate = LocalDate.now()
    val toDate = LocalDate.now().plusDays(flagVisitTaskConfiguration.numberOfDaysAhead.toLong().plus(1))
    return fromDate.datesUntil(toDate).toList()
  }

  private fun isVisitorBelowAllowedAge(dob: LocalDate?, visitDate: LocalDate, allowedAge: Int): Boolean = dob?.isAfter(visitDate.minusYears(allowedAge.toLong())) ?: false

  private fun getVisitNotifications(visitReference: String): List<NotificationEventType> = visitNotificationEventService.getNotificationsTypesForBookingReference(visitReference)
}
