package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.FLAGGED_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.UNFLAGGED_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason

@Service
class VisitNotificationFlaggingService(
  private val telemetryClientService: TelemetryClientService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun flagTrackEvents(
    visit: VisitDto,
    bookingEventAudit: EventAuditDto?,
    type: NotificationEventType,
  ) {
    LOG.info("Flagging visit with reference {} for ${type.reviewType}", visit.reference)
    val data = telemetryClientService.createFlagEventFromVisitDto(visit, bookingEventAudit, type)
    telemetryClientService.trackEvent(FLAGGED_VISIT_EVENT, data)
  }

  fun unFlagTrackEvents(
    visitReference: String,
    type: NotificationEventType?,
    reason: UnFlagEventReason,
    reasonText: String?,
  ) {
    LOG.info("Unflagging visit with reference {} , review type(s) - {}, reason - {} ", visitReference, type?.reviewType ?: "ALL", reason.desc)
    val data = telemetryClientService.createUnFlagEventForVisit(visitReference, type, reason, reasonText)
    telemetryClientService.trackEvent(UNFLAGGED_VISIT_EVENT, data)
  }
}
