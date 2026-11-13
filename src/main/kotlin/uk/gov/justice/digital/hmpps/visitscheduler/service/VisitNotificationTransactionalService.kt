package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.IGNORE_VISIT_NOTIFICATIONS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.PRISONER_RETURNED_TO_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ProcessVisitNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.SaveVisitNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VisitNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEventAttribute
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.PairedNotificationEventsUtil

@Service
@Transactional
class VisitNotificationTransactionalService(
  private val visitNotificationFlaggingService: VisitNotificationFlaggingService,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val pairedNotificationEventsUtil: PairedNotificationEventsUtil,
  private val visitRepository: VisitRepository,
  private val visitEventAuditService: VisitEventAuditService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun processVisitsWithNotifications(processVisitNotificationDto: ProcessVisitNotificationDto) {
    val affectedVisits = processVisitNotificationDto.affectedVisits.filter { it.visitType == VisitType.SOCIAL }

    affectedVisits.forEach {
      val bookingEventAudit = visitEventAuditService.getLastEventForBooking(it.reference)
      visitNotificationFlaggingService.flagTrackEvents(it, bookingEventAudit, processVisitNotificationDto.type)
    }

    if (pairedNotificationEventsUtil.isPairGroupRequired(processVisitNotificationDto.type)) {
      val affectedVisitsByDateMap = affectedVisits.groupBy { it.startTimestamp.toLocalDate() }
      affectedVisitsByDateMap.forEach {
        val affectedPairedVisits = pairedNotificationEventsUtil.pairWithEachOther(it.value)

        affectedPairedVisits.forEach { pairedVisits ->
          createPairedNotificationEvent(pairedVisits, processVisitNotificationDto.type)
        }
      }
    } else {
      val saveVisitNotificationDto = SaveVisitNotificationDto(
        affectedVisits,
        processVisitNotificationDto.type,
        processVisitNotificationDto.notificationEventAttributes,
      )

      saveVisitsNotification(saveVisitNotificationDto)
    }
  }

  fun deleteNotificationsThatAreNoLongerValid(
    visitNotificationEvents: List<VisitNotificationEvent>,
    notificationEventType: NotificationEventType,
    reason: UnFlagEventReason,
  ) {
    visitNotificationEvents.forEach {
      visitNotificationFlaggingService.unFlagTrackEvents(it.visit.reference, listOf(notificationEventType), reason, null)
    }
    visitNotificationEventRepository.deleteAll(visitNotificationEvents)
  }

  fun deleteNotificationsThatAreNoLongerValid(
    visitNotificationEvents: List<VisitNotificationEvent>,
    notificationEventTypes: List<NotificationEventType>,
    reason: UnFlagEventReason,
  ) {
    visitNotificationEvents.forEach {
      visitNotificationFlaggingService.unFlagTrackEvents(it.visit.reference, notificationEventTypes, reason, null)
    }
    visitNotificationEventRepository.deleteAll(visitNotificationEvents)
  }

  fun saveVisitNotification(
    impactedVisit: VisitDto,
    type: NotificationEventType,
    notificationEventAttributes: HashMap<NotificationEventAttributeType, String>? = null,
  ): String {
    val visit = visitRepository.findByReference(impactedVisit.reference) ?: throw VisitNotFoundException("Visit with reference  ${impactedVisit.reference} not found")
    val savedVisitNotificationEvent = visitNotificationEventRepository.save(
      VisitNotificationEvent(
        visit = visit,
        visitId = visit.id,
        type = type,
        bookingReference = visit.reference,
      ),
    )

    notificationEventAttributes?.forEach {
      savedVisitNotificationEvent.visitNotificationEventAttributes.add(
        VisitNotificationEventAttribute(
          attributeName = it.key,
          attributeValue = it.value,
          visitNotificationEventId = savedVisitNotificationEvent.id,
          visitNotificationEvent = savedVisitNotificationEvent,
        ),
      )
    }
    visitNotificationEventRepository.saveAndFlush(savedVisitNotificationEvent)

    visitEventAuditService.saveNotificationEventAudit(type, impactedVisit)

    return savedVisitNotificationEvent.reference
  }

  fun deleteVisitAndPairedNotificationEvents(visitReference: String, reason: UnFlagEventReason, text: String? = null) {
    deleteVisitNotificationEvents(visitReference, reason, text)
    val pairedNotificationEvents = visitNotificationEventRepository.getPairedVisitNotificationEvents(visitReference)
    // finally audit and delete any associated paired notifications
    deletePairedNotificationEvents(visitReference, pairedNotificationEvents, reason)
  }

  fun deletePairedNotificationEvents(changedVisitReference: String, pairedNotificationEvents: List<VisitNotificationEvent>, unFlagEventReason: UnFlagEventReason) {
    if (pairedNotificationEvents.isNotEmpty()) {
      pairedNotificationEvents.forEach {
        deleteVisitNotificationAndAddAuditEventForPairedVisits(it, changedVisitReference, unFlagEventReason)
      }
    }
  }

  fun deleteVisitNotificationAndAddAuditEventForPairedVisits(visitNotificationEvent: VisitNotificationEvent, changedVisitReference: String, unFlagEventReason: UnFlagEventReason, text: String? = null) {
    val pairedUnFlagEventReason = pairedNotificationEventsUtil.getPairedNotificationEventUnFlagReason(unFlagEventReason)
    val pairedUnFlagEventAuditType = pairedNotificationEventsUtil.getPairedVisitEventAuditType(pairedUnFlagEventReason)
    val pairedUnFlagEventAuditText: String = pairedNotificationEventsUtil.getPairedVisitEventAuditText(pairedUnFlagEventReason, changedVisitReference)

    visitNotificationEventRepository.deleteByReference(visitNotificationEvent.reference)

    // finally add an event audit for the paired event
    visitRepository.findByReference(visitNotificationEvent.visit.reference)?.let { pairedVisit ->
      visitEventAuditService.savePairedVisitChangeEventAudit(pairedVisit, pairedUnFlagEventAuditType, pairedUnFlagEventAuditText)
    }

    // after deleting the visit notifications - update application insights
    visitNotificationFlaggingService.unFlagTrackEvents(visitNotificationEvent.visit.reference, listOf(visitNotificationEvent.type), pairedUnFlagEventReason, text)
  }

  fun ignoreVisitNotifications(visit: VisitDto, ignoreVisitNotification: IgnoreVisitNotificationsDto): VisitDto {
    visitEventAuditService.saveIgnoreVisitNotificationEventAudit(ignoreVisitNotification.actionedBy, visit, ignoreVisitNotification.reason)
    deleteVisitAndPairedNotificationEvents(visit.reference, IGNORE_VISIT_NOTIFICATIONS, ignoreVisitNotification.reason)
    return visit
  }

  fun handleCourtVideoAppointment(currentVisitNotificationsForOriginalAppointment: List<VisitNotificationEvent>?, processVisitNotificationDto: ProcessVisitNotificationDto?) {
    if (!currentVisitNotificationsForOriginalAppointment.isNullOrEmpty()) {
      // Part 1 - Un-flag any visits that were flagged by the original appointment details
      deleteNotificationsThatAreNoLongerValid(
        currentVisitNotificationsForOriginalAppointment,
        NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT,
        UnFlagEventReason.COURT_VIDEO_APPOINTMENT_UPDATED,
      )
    }

    if (processVisitNotificationDto != null) {
      processVisitsWithNotifications(processVisitNotificationDto)
    }
  }

  fun handlePrisonerReceivedNotification(currentPrisonerReceivedNotificationsForPrison: List<VisitNotificationEvent>?, processVisitNotificationDto: ProcessVisitNotificationDto?) {
    // First flag visits from all prisons excluding the one the prisoner has moved to.
    if (processVisitNotificationDto != null) {
      processVisitsWithNotifications(processVisitNotificationDto)
    }

    // Second un-flag visits from current prison if any are flagged, as they are now at this prison.
    if (!currentPrisonerReceivedNotificationsForPrison.isNullOrEmpty()) {
      deleteNotificationsThatAreNoLongerValid(currentPrisonerReceivedNotificationsForPrison, PRISONER_RECEIVED_EVENT, PRISONER_RETURNED_TO_PRISON)
    }
  }

  @Transactional(readOnly = true)
  fun getNotificationCountForPrison(
    prisonCode: String,
    notificationEventTypes: List<NotificationEventType>?,
  ): Int = if (notificationEventTypes == null) {
    visitNotificationEventRepository.getNotificationGroupsCountByPrisonCode(prisonCode) ?: 0
  } else {
    visitNotificationEventRepository.getNotificationGroupsCountByPrisonCode(prisonCode, notificationEventTypes.map { it.name }.toList()) ?: 0
  }

  @Transactional(readOnly = true)
  fun getNotificationsTypesForBookingReference(bookingReference: String): List<NotificationEventType> = visitNotificationEventRepository.getNotificationsTypesForBookingReference(bookingReference)

  @Transactional(readOnly = true)
  fun getNotificationEventsForBookingReference(bookingReference: String): List<VisitNotificationEvent> = visitNotificationEventRepository.findVisitNotificationEventByVisitReference(bookingReference)

  private fun saveVisitsNotification(
    saveVisitNotificationDto: SaveVisitNotificationDto,
  ) {
    saveVisitNotificationDto.affectedVisits.forEach {
      saveVisitNotification(it, saveVisitNotificationDto.type, saveVisitNotificationDto.notificationEventAttributes)
    }
  }

  private fun createPairedNotificationEvent(pairedVisits: Pair<VisitDto, VisitDto>, notificationEventType: NotificationEventType) {
    createPairedNotificationEvent(pairedVisits.first, pairedVisits.second, notificationEventType)
    createPairedNotificationEvent(pairedVisits.second, pairedVisits.first, notificationEventType)
  }

  private fun createPairedNotificationEvent(visit1: VisitDto, visit2: VisitDto, notificationEventType: NotificationEventType) {
    val visitsPairedNotifications = visitNotificationEventRepository.findVisitNotificationEventByVisitReference(visit1.reference).filter { it.type == notificationEventType }
    val visitsPairedNotificationEventAttributes = visitsPairedNotifications.flatMap { it.visitNotificationEventAttributes }.filter { it.attributeName == NotificationEventAttributeType.PAIRED_VISIT }

    // if not already added as a paired visit
    if (!visitsPairedNotificationEventAttributes.map { it.attributeValue }.contains(visit2.reference)) {
      val notificationEventAttribute = hashMapOf(NotificationEventAttributeType.PAIRED_VISIT to visit2.reference)
      saveVisitNotification(visit1, notificationEventType, notificationEventAttribute)
    }
  }

  private fun deleteVisitNotificationEvents(visitReference: String, reason: UnFlagEventReason, text: String? = null) {
    val visitNotificationEvents = visitNotificationEventRepository.findVisitNotificationEventByVisitReference(visitReference)

    if (visitNotificationEvents.isNotEmpty()) {
      visitNotificationEventRepository.deleteVisitNotificationEventByVisitReference(visitReference)

      // after deleting the visit notifications - update application insights
      visitNotificationFlaggingService.unFlagTrackEvents(visitReference, visitNotificationEvents.map { it.type }, reason, text)
    }
  }
}
