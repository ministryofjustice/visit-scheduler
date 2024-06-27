package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
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
    telemetryClientService.trackFlagNotificationEvent(visit, bookingEventAudit, type)
  }

  fun unFlagTrackEvents(
    visitReference: String,
    type: NotificationEventType?,
    reason: UnFlagEventReason,
    reasonText: String?,
  ) {
    LOG.info("Unflagging visit with reference {} , review type(s) - {}, reason - {} ", visitReference, type?.reviewType ?: "ALL", reason.desc)
    telemetryClientService.trackUnFlagVisitNotificationEvent(visitReference, type, reason, reasonText)
  }
}
