package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.ADD_EXCLUDE_DATE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.APPLICATION_DELETED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.APPLICATION_SLOT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.FLAGGED_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.REMOVE_EXCLUDE_DATE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.UNFLAGGED_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_BOOKED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_CHANGED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_SLOT_RESERVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.PrisonerNotInSuppliedPrisonException
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class TelemetryClientService(
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  fun trackUpdateBookingEvent(
    bookingRequestDto: BookingRequestDto,
    bookedVisitDto: VisitDto,
    eventAuditDto: EventAuditDto,
  ) {
    trackEvent(
      VISIT_BOOKED_EVENT,
      createBookedVisitTrackData(bookedVisitDto, eventAuditDto, true),
    )
  }

  fun trackBookingEvent(
    bookingRequestDto: BookingRequestDto,
    bookedVisitDto: VisitDto,
    eventAuditDto: EventAuditDto,
  ) {
    trackEvent(
      VISIT_BOOKED_EVENT,
      createBookedVisitTrackData(bookedVisitDto, eventAuditDto, false),
    )
  }

  fun trackEventVisitChanged(applicationDto: ApplicationDto, bookingReference: String, eventAudit: EventAuditDto) {
    trackEvent(
      if (applicationDto.reserved) VISIT_SLOT_RESERVED_EVENT else VISIT_CHANGED_EVENT,
      createApplicationTrackData(
        applicationDto,
        bookingReference,
        eventAudit,
      ),
    )
  }

  fun trackEventApplicationReserved(applicationDto: ApplicationDto, eventAudit: EventAuditDto) {
    trackEvent(
      VISIT_SLOT_RESERVED_EVENT,
      createApplicationTrackData(
        applicationDto,
        eventAudit = eventAudit,
      ),
    )
  }

  fun trackEventApplicationDeleted(applicationDto: ApplicationDto) {
    trackEvent(
      APPLICATION_DELETED_EVENT,
      createApplicationTrackData(applicationDto),
    )
  }

  fun trackEventApplicationSlotChanged(applicationDto: ApplicationDto, bookingReference: String? = null) {
    val telemetryData = HashMap<String, String>()
    telemetryData["applicationReference"] = applicationDto.reference
    telemetryData["reservedSlot"] = applicationDto.reserved.toString()
    telemetryData["visitRestriction"] = applicationDto.visitRestriction.name
    bookingReference?.let {
      telemetryData["bookingReference"] = bookingReference
    }

    trackEvent(
      APPLICATION_SLOT_CHANGED_EVENT,
      telemetryData,
    )
  }

  fun trackCancelBookingEvent(
    visitDto: VisitDto,
    cancelVisitDto: CancelVisitDto,
    eventAudit: EventAuditDto,
  ) {
    val eventsMap = createCancelVisitTrackData(visitDto, eventAudit)
    trackEvent(VISIT_CANCELLED_EVENT, eventsMap)
  }

  fun trackFlagNotificationEvent(
    visit: VisitDto,
    bookingEventAudit: EventAuditDto?,
    type: NotificationEventType,
  ) {
    val data = createFlagData(visit, bookingEventAudit, type)
    trackEvent(FLAGGED_VISIT_EVENT, data)
  }

  fun trackUnFlagVisitNotificationEvent(
    visitReference: String,
    notificationEventTypesUnflagged: String,
    reason: UnFlagEventReason,
    reasonText: String?,
  ) {
    val data = createUnFlagData(visitReference, notificationEventTypesUnflagged, reason, reasonText)
    trackEvent(UNFLAGGED_VISIT_EVENT, data)
  }

  fun trackVisitCountsEvent(sessionReport: SessionVisitCountsDto) {
    val event = createVisitCountTelemetryData(sessionReport)
    trackEvent(TelemetryVisitEvents.VISIT_COUNTS_REPORT, event)
  }

  fun trackOverbookedSessionsEvent(overbookedSession: OverbookedSessionsDto) {
    val event = createOverbookedSessionTelemetryData(overbookedSession)
    trackEvent(TelemetryVisitEvents.OVERBOOKED_SESSION_REPORT, event)
  }

  fun trackFlaggedVisitEvent(visit: VisitDto, exception: Exception?) {
    val visitTrackEvent = createFlaggedVisitTrackEvent(visit, exception)
    trackEvent(FLAGGED_VISIT_EVENT, visitTrackEvent)
  }

  fun trackAddExcludeDateEvent(prisonCode: String, prisonExcludeDateDto: PrisonExcludeDateDto) {
    val visitTrackEvent = createExcludeDateEventData(prisonCode, prisonExcludeDateDto)
    trackEvent(ADD_EXCLUDE_DATE_EVENT, visitTrackEvent)
  }

  fun trackRemoveExcludeDateEvent(prisonCode: String, prisonExcludeDateDto: PrisonExcludeDateDto) {
    val visitTrackEvent = createExcludeDateEventData(prisonCode, prisonExcludeDateDto)
    trackEvent(REMOVE_EXCLUDE_DATE_EVENT, visitTrackEvent)
  }

  private fun handleException(visit: VisitDto, visitTrackEvent: MutableMap<String, String>, e: Exception) {
    visitTrackEvent["hasException"] = "true"
    if (e is PrisonerNotInSuppliedPrisonException) {
      visitTrackEvent["hasPrisonerMoved"] = "true"
      visitTrackEvent["additionalInformation"] = e.message ?: "Prisoner has moved"
    } else {
      visitTrackEvent["additionalInformation"] = e.message ?: "An exception occurred"
    }
    VisitTask.log.info("Flagged Visit: $e raised for Visit with reference - ${visit.reference} ,prisoner id - ${visit.prisonerId}, prison code - ${visit.prisonCode}, start time - ${visit.startTimestamp}, end time - ${visit.endTimestamp}, error message - ${e.message}")
  }

  private fun trackEvent(visitEvent: TelemetryVisitEvents, properties: Map<String, String>) {
    try {
      telemetryClient.trackEvent(visitEvent.eventName, properties, null)
    } catch (e: RuntimeException) {
      LOG.error("Error occurred in call to telemetry client to log event - $e.toString()")
    }
  }

  private fun createVisitCountTelemetryData(
    sessionReport: SessionVisitCountsDto,
  ): Map<String, String> {
    val reportEvent = mutableMapOf<String, String>()
    reportEvent["reportDate"] = formatDateToString(sessionReport.reportDate)
    reportEvent["prisonCode"] = sessionReport.prisonCode
    reportEvent["blockedDate"] = sessionReport.isBlockedDate.toString()
    reportEvent["hasSessions"] = sessionReport.hasSessionsOnDate.toString()

    sessionReport.sessionTimeSlot?.let {
      reportEvent["sessionStart"] = formatTimeToString(it.startTime)
      reportEvent["sessionEnd"] = formatTimeToString(it.endTime)
    }
    sessionReport.sessionCapacity?.let {
      reportEvent["openCapacity"] = it.open.toString()
      reportEvent["closedCapacity"] = it.closed.toString()
    }
    sessionReport.visitType?.let {
      reportEvent["visitType"] = it.toString()
    }
    sessionReport.openBookedCount?.let {
      reportEvent["openBooked"] = it.toString()
    }
    sessionReport.closedBookedCount?.let {
      reportEvent["closedBooked"] = it.toString()
    }
    sessionReport.openCancelledCount?.let {
      reportEvent["openCancelled"] = it.toString()
    }
    sessionReport.closedCancelledCount?.let {
      reportEvent["closedCancelled"] = it.toString()
    }

    return reportEvent.toMap()
  }

  private fun createOverbookedSessionTelemetryData(
    overbookedSession: OverbookedSessionsDto,
  ): Map<String, String> {
    val reportEvent = mutableMapOf<String, String>()
    with(overbookedSession) {
      reportEvent["sessionDate"] = formatDateToString(sessionDate)
      reportEvent["prisonId"] = prisonCode
      reportEvent["sessionStart"] = formatTimeToString(sessionTimeSlot.startTime)
      reportEvent["sessionEnd"] = formatTimeToString(sessionTimeSlot.endTime)
      reportEvent["openCapacity"] = sessionCapacity.open.toString()
      reportEvent["closedCapacity"] = sessionCapacity.closed.toString()
      reportEvent["openVisits"] = openCount.toString()
      reportEvent["closedVisits"] = closedCount.toString()
    }

    return reportEvent.toMap()
  }

  private fun createCancelVisitTrackData(
    visitDto: VisitDto,
    eventAudit: EventAuditDto,
  ): MutableMap<String, String> {
    val data = createDefaultVisitData(visitDto)
    createEventAuditData(eventAudit, data)
    visitDto.outcomeStatus?.let {
      data.put("outcomeStatus", it.name)
    }
    return data
  }

  private fun createBookedVisitTrackData(
    visitDto: VisitDto,
    eventAudit: EventAuditDto,
    isUpdate: Boolean = false,
  ): MutableMap<String, String> {
    val data = createDefaultVisitData(visitDto)
    data["isUpdated"] = isUpdate.toString()
    visitDto.visitorSupport?.let {
      data.put("supportRequired", it.description)
    }

    createEventAuditData(eventAudit, data)

    return data
  }

  private fun createFlaggedVisitTrackEvent(visit: VisitDto, exception: Exception?): MutableMap<String, String> {
    val flagVisitMap = mutableMapOf(
      "reference" to visit.reference,
      "prisonerId" to visit.prisonerId,
      "prisonId" to visit.prisonCode,
      "visitType" to visit.visitType.name,
      "visitRestriction" to visit.visitRestriction.name,
      "visitStart" to formatDateTimeToString(visit.startTimestamp),
      "visitEnd" to formatDateTimeToString(visit.endTimestamp),
      "visitStatus" to visit.visitStatus.name,
    )

    val eventAudit = visitEventAuditService.getLastEventForBooking(visit.reference)
    createEventAuditData(eventAudit, flagVisitMap)

    exception?.let {
      handleException(visit, flagVisitMap, exception)
    }

    return flagVisitMap
  }

  private fun createApplicationTrackData(
    applicationDto: ApplicationDto,
    visitReference: String? = null,
    eventAudit: EventAuditDto? = null,
  ): MutableMap<String, String> {
    val data = mutableMapOf(
      "reference" to (visitReference ?: ""),
      "applicationReference" to applicationDto.reference,
      "prisonerId" to applicationDto.prisonerId,
      "prisonId" to applicationDto.prisonCode,
      "visitType" to applicationDto.visitType.name,
      "visitRestriction" to applicationDto.visitRestriction.name,
      "visitStart" to formatDateTimeToString(applicationDto.startTimestamp),
      "visitEnd" to formatDateTimeToString(applicationDto.endTimestamp),
      "reserved" to applicationDto.reserved.toString(),
      "userType" to applicationDto.userType.toString(),
    )

    createEventAuditData(eventAudit, data)

    return data
  }

  private fun createFlagData(
    visitDto: VisitDto,
    bookingEventAudit: EventAuditDto?,
    notificationEventType: NotificationEventType,
  ): MutableMap<String, String> {
    val data = createDefaultVisitData(visitDto)
    data["reviewType"] = notificationEventType.reviewType
    bookingEventAudit?.let {
      data["visitBooked"] = formatDateTimeToString(it.createTimestamp)
    }
    createEventAuditData(bookingEventAudit, data)
    return data
  }

  private fun createDefaultVisitData(
    visitDto: VisitDto,
  ): MutableMap<String, String> {
    return mutableMapOf(
      "reference" to visitDto.reference,
      "applicationReference" to visitDto.applicationReference,
      "prisonerId" to visitDto.prisonerId,
      "prisonId" to visitDto.prisonCode,
      "visitStatus" to visitDto.visitStatus.name,
      "visitRestriction" to visitDto.visitRestriction.name,
      "visitStart" to formatDateTimeToString(visitDto.startTimestamp),
      "visitEnd" to formatDateTimeToString(visitDto.endTimestamp),
      "visitType" to visitDto.visitType.name,
      "visitRoom" to visitDto.visitRoom,
      "hasPhoneNumber" to (visitDto.visitContact.telephone != null).toString(),
      "hasEmail" to (visitDto.visitContact.email != null).toString(),
      "totalVisitors" to visitDto.visitors.size.toString(),
      "visitors" to getVisitorIdsAsString(visitDto.visitors),
    )
  }

  private fun createUnFlagData(
    visitReference: String,
    notificationEventTypesUnflagged: String,
    reason: UnFlagEventReason,
    reasonText: String? = null,
  ): MutableMap<String, String> {
    val unFlagEventDataMap = mutableMapOf(
      "reference" to visitReference,
      "reason" to reason.desc,
      "reviewTypes" to notificationEventTypesUnflagged,
    )

    reasonText?.let {
      unFlagEventDataMap["text"] = it
    }

    return unFlagEventDataMap
  }

  private fun createEventAuditData(
    eventAudit: EventAuditDto?,
    data: MutableMap<String, String>,
  ) {
    eventAudit?.let {
      it.actionedBy.userName?.let { userName ->
        data["actionedBy"] = userName
      }
      data["source"] = it.actionedBy.userType.name
      data["applicationMethodType"] = it.applicationMethodType.name
    }
  }

  private fun formatDateTimeToString(dateTime: LocalDateTime): String {
    return dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME)
  }

  private fun formatTimeToString(time: LocalTime): String {
    return time.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_TIME)
  }

  private fun formatDateToString(date: LocalDate): String {
    return date.format(DateTimeFormatter.ISO_DATE)
  }

  private fun getVisitorIdsAsString(visitors: List<VisitorDto>): String {
    return visitors.map { it.nomisPersonId }.joinToString(",")
  }

  private fun createExcludeDateEventData(
    prisonCode: String,
    excludeDateDto: PrisonExcludeDateDto,
  ): Map<String, String> {
    val excludeDateEvent = mutableMapOf<String, String>()
    excludeDateEvent["prisonId"] = prisonCode
    excludeDateEvent["excludedDate"] = formatDateToString(excludeDateDto.excludeDate)

    // TODO - remove the ?: "NOT_KNOWN" later
    excludeDateEvent["actionedBy"] = excludeDateDto.actionedBy ?: "NOT_KNOWN"
    return excludeDateEvent.toMap()
  }
}
