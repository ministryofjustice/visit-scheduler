package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.IGNORE_VISIT_NOTIFICATIONS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.NON_ASSOCIATION_VISIT_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.NON_ASSOCIATION_VISIT_IGNORED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.NON_ASSOCIATION_VISIT_UPDATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.PAIRED_VISIT_CANCELLED_IGNORED_OR_UPDATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISIT_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISIT_UPDATED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent

@Service
class PairedNotificationEventsUtil {
  private val pairedNotificationEventTypes: List<NotificationEventType> = listOf(NON_ASSOCIATION_EVENT)
  private val pairedNotificationsEventUnFlagReasonMap = mapOf(
    VISIT_CANCELLED to NON_ASSOCIATION_VISIT_CANCELLED,
    VISIT_UPDATED to NON_ASSOCIATION_VISIT_UPDATED,
    IGNORE_VISIT_NOTIFICATIONS to NON_ASSOCIATION_VISIT_IGNORED,
  )
  private val pairedNotificationsEventUnFlagEventAuditMap = mapOf(
    NON_ASSOCIATION_VISIT_CANCELLED to EventAuditType.CANCELLED_NON_ASSOCIATION_VISIT_EVENT,
    NON_ASSOCIATION_VISIT_UPDATED to EventAuditType.UPDATED_NON_ASSOCIATION_VISIT_EVENT,
    NON_ASSOCIATION_VISIT_IGNORED to EventAuditType.IGNORED_NON_ASSOCIATION_VISIT_NOTIFICATIONS_EVENT,
  )
  private val pairedNotificationsEventUnFlagEventReasonMap = mapOf(
    NON_ASSOCIATION_VISIT_CANCELLED to "Non-association's visit with reference - %s was cancelled.",
    NON_ASSOCIATION_VISIT_UPDATED to "Non-association's visit with reference - %s was updated.",
    NON_ASSOCIATION_VISIT_IGNORED to "Non-association's visit with reference - %s's notifications were ignored.",
  )

  fun getPairedNotificationEvents(visitNotificationEvents: List<VisitNotificationEvent>): List<VisitNotificationEvent> = visitNotificationEvents.filter { pairedNotificationEventTypes.contains(it.type) }

  fun getPairedNotificationEventUnFlagReason(unFlagEventReason: UnFlagEventReason) = pairedNotificationsEventUnFlagReasonMap[unFlagEventReason] ?: PAIRED_VISIT_CANCELLED_IGNORED_OR_UPDATED

  fun getPairedVisitEventAuditType(unFlagEventReason: UnFlagEventReason) = pairedNotificationsEventUnFlagEventAuditMap[unFlagEventReason] ?: EventAuditType.PAIRED_VISIT_CANCELLED_IGNORED_OR_UPDATED_EVENT

  fun getPairedVisitEventAuditText(unFlagEventReason: UnFlagEventReason, visitReference: String): String {
    val reason = pairedNotificationsEventUnFlagEventReasonMap[unFlagEventReason] ?: "Paired visit with reference - %s was cancelled, ignored or updated."
    return String.format(reason, visitReference)
  }

  fun isPairGroupRequired(
    type: NotificationEventType,
  ) = pairedNotificationEventTypes.contains(type)

  /**
   * Groups List into pairs e.g.
   *  A,B,C,D
   *  Becomes : AB, AC, AD, BC, BD, CD
   *  Ignores : AA, BB ,CC
   */
  fun pairWithEachOther(affectedVisits: List<VisitDto>): List<Pair<VisitDto, VisitDto>> {
    val result: MutableList<Pair<VisitDto, VisitDto>> = mutableListOf()
    affectedVisits.forEachIndexed { index, visitDto ->
      for (secondIndex in index + 1..<affectedVisits.size) {
        val otherVisit = affectedVisits[secondIndex]
        if (visitDto.prisonerId != otherVisit.prisonerId) {
          result.add(Pair(visitDto, otherVisit))
        }
      }
    }
    return result
  }
}
