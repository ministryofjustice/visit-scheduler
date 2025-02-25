package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.ActionedByDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.SESSION_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.VISITOR_RESTRICTION_UPSERTED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.VISITOR_UNAPPROVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TRANSFERRED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.IGNORE_VISIT_NOTIFICATIONS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.NON_ASSOCIATION_REMOVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.PRISONER_ALERT_CODE_REMOVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.PRISONER_RETURNED_TO_PRISON
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.PRISON_EXCLUDE_DATE_REMOVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.SESSION_EXCLUDE_DATE_REMOVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISITOR_APPROVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertCreatedUpdatedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerVisitsNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ProcessVisitNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.SaveVisitNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.SessionDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventAttributeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorApprovedUnapprovedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEventAttribute
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class VisitNotificationEventService(
  private val visitService: VisitService,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val prisonerService: PrisonerService,
  private val visitNotificationFlaggingService: VisitNotificationFlaggingService,
) {

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleNonAssociations(notificationDto: NonAssociationChangedNotificationDto) {
    LOG.debug("NonAssociations notification received : {}", notificationDto)
    if (NON_ASSOCIATION_CREATED == notificationDto.type) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
      prisonCode?.let {
        val affectedVisits = getOverLappingVisits(notificationDto, prisonCode)

        val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, NON_ASSOCIATION_EVENT, null)
        processVisitsWithNotifications(processVisitNotificationDto)
      }
    } else if (notificationDto.type in arrayOf(NON_ASSOCIATION_DELETED, NON_ASSOCIATION_CLOSED)) {
      if (!prisonerService.hasPrisonerGotANonAssociationWith(notificationDto.prisonerNumber, notificationDto.nonAssociationPrisonerNumber)) {
        val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
        prisonCode?.let {
          val affectedNotifications = getAffectedNotifications(notificationDto, it)
          deleteNotificationsThatAreNoLongerValid(affectedNotifications, NON_ASSOCIATION_EVENT, NON_ASSOCIATION_REMOVED)
        }
      }
    }
  }

  @Transactional
  fun handleAddPrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    LOG.debug("PrisonVisitBlockDate notification received : {}", prisonDateBlockedDto)
    val affectedVisits = visitService.getBookedVisitsForDate(prisonCode = prisonDateBlockedDto.prisonCode, date = prisonDateBlockedDto.visitDate)
    val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PRISON_VISITS_BLOCKED_FOR_DATE, null)
    processVisitsWithNotifications(processVisitNotificationDto)
  }

  @Transactional
  fun handleRemovePrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    LOG.debug("RemovePrisonVisitBlockDate notification received : {}", prisonDateBlockedDto)
    val affectedNotifications = visitNotificationEventRepository.getEventsByVisitDate(
      prisonDateBlockedDto.prisonCode,
      prisonDateBlockedDto.visitDate,
      PRISON_VISITS_BLOCKED_FOR_DATE,
    )
    deleteNotificationsThatAreNoLongerValid(affectedNotifications, PRISON_VISITS_BLOCKED_FOR_DATE, PRISON_EXCLUDE_DATE_REMOVED)
  }

  @Transactional
  fun handleAddSessionVisitBlockDate(sessionDateBlockedDto: SessionDateBlockedDto) {
    LOG.debug("Add session block date notification received : {}", sessionDateBlockedDto)
    with(sessionDateBlockedDto) {
      val affectedVisits = visitService.getBookedVisitsBySessionForDate(sessionTemplateReference, visitDate)
      val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, SESSION_VISITS_BLOCKED_FOR_DATE, null)
      processVisitsWithNotifications(processVisitNotificationDto)
    }
  }

  @Transactional
  fun handleRemoveSessionVisitBlockDate(sessionDateBlockedDto: SessionDateBlockedDto) {
    LOG.debug("Remove session block date notification received : {}", sessionDateBlockedDto)
    with(sessionDateBlockedDto) {
      val affectedNotifications = visitNotificationEventRepository.getEventsByVisitDate(sessionTemplateReference, visitDate, SESSION_VISITS_BLOCKED_FOR_DATE)

      deleteNotificationsThatAreNoLongerValid(affectedNotifications, SESSION_VISITS_BLOCKED_FOR_DATE, SESSION_EXCLUDE_DATE_REMOVED)
    }
  }

  @Transactional
  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    LOG.debug("PrisonerReleasedNotification notification received : {}", notificationDto)
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureBookedVisits(notificationDto.prisonerNumber, notificationDto.prisonCode)
      val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PRISONER_RELEASED_EVENT, null)
      processVisitsWithNotifications(processVisitNotificationDto)
    }
  }

  @Transactional
  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    LOG.debug("PrisonerRestrictionChange notification received")
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)

      val startDateTime = (if (LocalDate.now() > notificationDto.validFromDate) LocalDate.now() else notificationDto.validFromDate).atStartOfDay()
      val endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX)
      val affectedVisits = visitService.getFutureBookedVisits(notificationDto.prisonerNumber, prisonCode, startDateTime, endDateTime)

      val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PRISONER_RESTRICTION_CHANGE_EVENT, null)

      processVisitsWithNotifications(processVisitNotificationDto)
    }
  }

  @Transactional
  fun handlePrisonerAlertCreatedUpdatedNotification(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("handlePrisonerAlertCreatedUpdated notification received")

    if (notificationDto.alertsAdded.isNotEmpty()) {
      processAlertsAdded(notificationDto)
    }

    // An additional check is made on activeAlerts being empty, because if the activeAlerts
    // is not empty, then we wouldn't want to un-flag any visits as the visit may be flagged for those active alerts.
    if (notificationDto.alertsRemoved.isNotEmpty() && notificationDto.activeAlerts.isEmpty()) {
      processAlertsRemoved(notificationDto)
    }
  }

  private fun processAlertsAdded(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("Entered handlePrisonerAlertCreatedUpdated processAlertsAdded")

    val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
    val affectedVisits = visitService.getFutureBookedVisits(notificationDto.prisonerNumber, prisonCode)

    val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PRISONER_ALERTS_UPDATED_EVENT, null)
    processVisitsWithNotifications(processVisitNotificationDto)
  }

  private fun processAlertsRemoved(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("Entered handlePrisonerAlertCreatedUpdated processAlertsRemoved")

    val prisonerDetails = prisonerService.getPrisoner(notificationDto.prisonerNumber)
    prisonerDetails?.let { prisoner ->
      prisoner.prisonCode?.let {
        val currentPrisonNotifications = visitNotificationEventRepository.getEventsBy(
          notificationDto.prisonerNumber,
          prisoner.prisonCode!!,
          PRISONER_ALERTS_UPDATED_EVENT,
        )

        deleteNotificationsThatAreNoLongerValid(
          currentPrisonNotifications,
          PRISONER_ALERTS_UPDATED_EVENT,
          PRISONER_ALERT_CODE_REMOVED,
        )
      }
    }
  }

  @Transactional
  fun handlePersonRestrictionUpsertedNotification(notificationDto: PersonRestrictionUpsertedNotificationDto) {
    LOG.debug("PersonRestrictionUpsertedNotificationDto notification received : {}", notificationDto)

    val visitorSupportedRestrictionTypes = VisitorSupportedRestrictionType.entries.map { it.name }.toSet()
    if (isNotificationDatesValid(notificationDto.validToDate) && visitorSupportedRestrictionTypes.contains(notificationDto.restrictionType)) {
      // PersonRestrictionUpsertedNotification is a local version of the global VisitorRestrictionChangeNotification event.
      // Hence, the need for the prisonerId, to only flag visits between the given visitor and prisoner.
      val affectedVisits = visitService.getFutureVisitsByVisitorId(
        visitorId = notificationDto.visitorId,
        prisonerId = notificationDto.prisonerNumber,
        endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX),
      )
      if (affectedVisits.isNotEmpty()) {
        val notificationAttributes = hashMapOf(
          NotificationEventAttributeType.VISITOR_RESTRICTION to notificationDto.restrictionType,
          NotificationEventAttributeType.VISITOR_ID to notificationDto.visitorId,
        )
        val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PERSON_RESTRICTION_UPSERTED_EVENT, notificationAttributes)

        processVisitsWithNotifications(processVisitNotificationDto)
      }
    }
  }

  @Transactional
  fun handleVisitorRestrictionUpsertedNotification(notificationDto: VisitorRestrictionUpsertedNotificationDto) {
    LOG.debug("VisitorRestrictionUpsertedNotificationDto notification received : {}", notificationDto)

    val visitorSupportedRestrictionTypes = VisitorSupportedRestrictionType.entries.map { it.name }.toSet()
    if (isNotificationDatesValid(notificationDto.validToDate) && visitorSupportedRestrictionTypes.contains(notificationDto.restrictionType)) {
      // VisitorRestrictionUpsertedNotificationDto is the global version of the local PersonRestrictionUpsertedNotificationDto event.
      // Hence, no prisonerId is given, so we flag every visit they have.
      val affectedVisits = visitService.getFutureVisitsByVisitorId(
        visitorId = notificationDto.visitorId,
        endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX),
      )
      if (affectedVisits.isNotEmpty()) {
        val notificationAttributes = hashMapOf(
          NotificationEventAttributeType.VISITOR_RESTRICTION to notificationDto.restrictionType,
          NotificationEventAttributeType.VISITOR_ID to notificationDto.visitorId,
        )
        val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, VISITOR_RESTRICTION_UPSERTED_EVENT, notificationAttributes)
        processVisitsWithNotifications(processVisitNotificationDto)
      }
    }
  }

  @Transactional
  fun handleVisitorUnapprovedNotification(notificationDto: VisitorApprovedUnapprovedNotificationDto) {
    LOG.debug("handleVisitorUnapprovedNotification notification received : {}", notificationDto)

    val affectedVisits = visitService.getFutureVisitsByVisitorId(
      visitorId = notificationDto.visitorId,
      prisonerId = notificationDto.prisonerNumber,
    )

    if (affectedVisits.isNotEmpty()) {
      val notificationAttributes = hashMapOf(
        NotificationEventAttributeType.VISITOR_ID to notificationDto.visitorId,
      )
      val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, VISITOR_UNAPPROVED_EVENT, notificationAttributes)
      processVisitsWithNotifications(processVisitNotificationDto)
    }
  }

  @Transactional
  fun handleVisitorApprovedNotification(notificationDto: VisitorApprovedUnapprovedNotificationDto) {
    LOG.debug("handleVisitorApprovedNotification notification received : {}", notificationDto)

    val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
    prisonCode?.let {
      val currentVisitorUnApprovedNotifications = visitNotificationEventRepository.getEventsByVisitor(
        prisonerNumber = notificationDto.prisonerNumber,
        prisonCode = prisonCode,
        visitorId = notificationDto.visitorId.toLong(),
        notificationEvent = VISITOR_UNAPPROVED_EVENT,
      )

      deleteNotificationsThatAreNoLongerValid(
        currentVisitorUnApprovedNotifications,
        VISITOR_UNAPPROVED_EVENT,
        VISITOR_APPROVED,
      )
    } ?: {
      LOG.error("handleVisitorApprovedNotification cannot process as prisonCode is null - notificationDto - $notificationDto")
    }
  }

  @Transactional
  fun handlePrisonerReceivedNotification(notificationDto: PrisonerReceivedNotificationDto) {
    LOG.debug("PrisonerReceivedNotification notification received : {}", notificationDto)
    if (TRANSFERRED == notificationDto.reason) {
      // First flag visits from all prisons excluding the one the prisoner has moved to.
      val affectedVisits = visitService.getFutureBookedVisitsExcludingPrison(notificationDto.prisonerNumber, notificationDto.prisonCode)
      if (affectedVisits.isNotEmpty()) {
        val processVisitNotificationDto = ProcessVisitNotificationDto(affectedVisits, PRISONER_RECEIVED_EVENT, null)
        processVisitsWithNotifications(processVisitNotificationDto)
      }

      // Second un-flag visits from current prison if any are flagged, as they are now at this prison.
      val currentPrisonNotifications = visitNotificationEventRepository.getEventsBy(notificationDto.prisonerNumber, notificationDto.prisonCode, PRISONER_RECEIVED_EVENT)
      deleteNotificationsThatAreNoLongerValid(currentPrisonNotifications, PRISONER_RECEIVED_EVENT, PRISONER_RETURNED_TO_PRISON)
    }
  }

  private fun processVisitsWithNotifications(processVisitNotificationDto: ProcessVisitNotificationDto) {
    val affectedVisitsNoDuplicate = processVisitNotificationDto.affectedVisits.filter { !visitNotificationEventRepository.isEventARecentDuplicate(it.reference, processVisitNotificationDto.type) }

    affectedVisitsNoDuplicate.forEach {
      val bookingEventAudit = visitEventAuditService.getLastEventForBooking(it.reference)
      visitNotificationFlaggingService.flagTrackEvents(it, bookingEventAudit, processVisitNotificationDto.type)
    }

    if (isPairGroupRequired(processVisitNotificationDto.type)) {
      val affectedPairedVisits = pairWithEachOther(processVisitNotificationDto.affectedVisits)
      affectedPairedVisits.forEach {
        if (!visitNotificationEventRepository.isEventARecentPairedDuplicate(it.first.reference, it.second.reference, processVisitNotificationDto.type)) {
          saveGroupedVisitsNotification(it.toList(), processVisitNotificationDto.type)
        }
      }
    } else {
      val saveVisitNotificationDto = SaveVisitNotificationDto(
        affectedVisitsNoDuplicate,
        processVisitNotificationDto.type,
        processVisitNotificationDto.notificationEventAttributes,
      )

      saveVisitsNotification(saveVisitNotificationDto)
    }
  }

  private fun isPairGroupRequired(
    type: NotificationEventType,
  ) = NON_ASSOCIATION_EVENT == type

  /**
   * Groups List into pairs e.g.
   *  A,B,C,D
   *  Becomes : AB, AC, AD, BC, BD, CD
   *  Ignores : AA, BB ,CC
   */
  private fun pairWithEachOther(affectedVisits: List<VisitDto>): List<Pair<VisitDto, VisitDto>> {
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

  private fun saveGroupedVisitsNotification(
    affectedVisitsNoDuplicate: List<VisitDto>,
    type: NotificationEventType,
  ) {
    var reference: String? = null
    affectedVisitsNoDuplicate.forEach {
      reference = saveVisitNotification(it, reference, type)
    }
  }

  private fun saveVisitsNotification(
    saveVisitNotificationDto: SaveVisitNotificationDto,
  ) {
    saveVisitNotificationDto.affectedVisits.forEach {
      saveVisitNotification(it, null, saveVisitNotificationDto.type, saveVisitNotificationDto.notificationEventAttributes)
    }
  }

  private fun deleteNotificationsThatAreNoLongerValid(
    visitNotificationEvents: List<VisitNotificationEvent>,
    notificationEventType: NotificationEventType,
    reason: UnFlagEventReason,
  ) {
    visitNotificationEvents.forEach {
      visitNotificationFlaggingService.unFlagTrackEvents(it.bookingReference, listOf(notificationEventType), reason, null)
    }
    visitNotificationEventRepository.deleteAll(visitNotificationEvents)
  }

  private fun saveVisitNotification(
    impactedVisit: VisitDto,
    reference: String?,
    type: NotificationEventType,
    notificationEventAttributes: HashMap<NotificationEventAttributeType, String>? = null,
  ): String {
    val savedVisitNotificationEvent = visitNotificationEventRepository.save(
      if (reference == null) {
        VisitNotificationEvent(
          bookingReference = impactedVisit.reference,
          type = type,
        )
      } else {
        VisitNotificationEvent(
          bookingReference = impactedVisit.reference,
          type = type,
          _reference = reference,
        )
      },
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

  private fun getOverLappingVisits(notificationDto: NonAssociationChangedNotificationDto, prisonCode: String): List<VisitDto> {
    val fromDate = LocalDate.now()

    val primaryPrisonerVisits = visitService.getBookedVisits(notificationDto.prisonerNumber, prisonCode, fromDate)
    val nonAssociationPrisonerVisits = visitService.getBookedVisits(notificationDto.nonAssociationPrisonerNumber, prisonCode, fromDate)
    return getOverLappingVisits(primaryPrisonerVisits, nonAssociationPrisonerVisits)
  }

  private fun getAffectedNotifications(notificationDto: NonAssociationChangedNotificationDto, prisonCode: String): List<VisitNotificationEvent> {
    val prisonersNotifications = visitNotificationEventRepository.getEventsBy(notificationDto.prisonerNumber, prisonCode, NON_ASSOCIATION_EVENT)
    val nonAssociationPrisonersNotifications = visitNotificationEventRepository.getEventsBy(notificationDto.nonAssociationPrisonerNumber, prisonCode, NON_ASSOCIATION_EVENT)

    val prisonerEventsToBeDeleted = prisonersNotifications.filter { prisonersNotify ->
      nonAssociationPrisonersNotifications.any { it.reference == prisonersNotify.reference }
    }
    val nsPrisonerEventsToBeDeleted = nonAssociationPrisonersNotifications.filter { nsPrisonersNotify ->
      prisonerEventsToBeDeleted.any { it.reference == nsPrisonersNotify.reference }
    }

    return (prisonerEventsToBeDeleted + nsPrisonerEventsToBeDeleted).sortedBy { it.reference }
  }

  private fun getOverLappingVisits(primaryPrisonerVisits: List<VisitDto>, nonAssociationPrisonerVisits: List<VisitDto>): List<VisitDto> {
    val overlappingVisits = mutableListOf<VisitDto>()
    if (primaryPrisonerVisits.isNotEmpty() && nonAssociationPrisonerVisits.isNotEmpty()) {
      val overlappingVisitDates = getOverlappingVisitDatesByPrison(primaryPrisonerVisits, nonAssociationPrisonerVisits)
      if (overlappingVisitDates.isNotEmpty()) {
        overlappingVisits.addAll(getVisitsForDateAndPrison(primaryPrisonerVisits, overlappingVisitDates))
        overlappingVisits.addAll(getVisitsForDateAndPrison(nonAssociationPrisonerVisits, overlappingVisitDates))
      }
    }
    return overlappingVisits.toList()
  }

  private fun getVisitsForDateAndPrison(visits: List<VisitDto>, visitDatesByPrison: List<Pair<LocalDate, String>>): List<VisitDto> = visits.filter {
    visitDatesByPrison.contains(Pair(it.startTimestamp.toLocalDate(), it.prisonCode))
  }

  private fun getOverlappingVisitDatesByPrison(primaryPrisonerVisits: List<VisitDto>, nonAssociationPrisonerVisits: List<VisitDto>): List<Pair<LocalDate, String>> {
    // all visits by date and prison for first prisoner
    val primaryPrisonerVisitDatesByPrison = primaryPrisonerVisits.map { Pair(it.startTimestamp.toLocalDate(), it.prisonCode) }.toSet()
    // all visits by date and prison for second prisoner
    val nonAssociationPrisonerVisitDatesByPrison = nonAssociationPrisonerVisits.map { Pair(it.startTimestamp.toLocalDate(), it.prisonCode) }.toSet()

    // return list of matching date and prison pair
    return primaryPrisonerVisitDatesByPrison.filter { nonAssociationPrisonerVisitDatesByPrison.contains(it) }
  }

  private fun isNotificationDatesValid(validToDate: LocalDate?): Boolean {
    val toDate = getValidToDateTime(validToDate)
    return (toDate == null) || toDate.isAfter(LocalDateTime.now())
  }

  private fun getValidToDateTime(validToDate: LocalDate?): LocalDateTime? = validToDate?.let { LocalDateTime.of(validToDate, LocalTime.MAX) }

  fun getNotificationCountForPrison(
    prisonCode: String,
    notificationEventTypes: List<NotificationEventType>?,
  ): Int {
    LOG.debug("getNotificationCountForPrison with prisonCode - {}, notificationEventTypes - {}", prisonCode, notificationEventTypes)
    return if (notificationEventTypes == null) {
      this.visitNotificationEventRepository.getNotificationGroupsCountByPrisonCode(prisonCode) ?: 0
    } else {
      this.visitNotificationEventRepository.getNotificationGroupsCountByPrisonCode(prisonCode, notificationEventTypes.map { it.name }.toList()) ?: 0
    }
  }

  fun getFutureNotificationVisitGroups(prisonCode: String): List<NotificationGroupDto> {
    val futureNotifications = this.visitNotificationEventRepository.getFutureVisitNotificationEvents(prisonCode)
    val eventGroups = futureNotifications.groupByTo(mutableMapOf()) { it.reference }
    val notificationGroupDtos = mutableListOf<NotificationGroupDto>()
    eventGroups.forEach { (reference, events) ->
      notificationGroupDtos.add(
        NotificationGroupDto(
          reference,
          events.first().type,
          createPrisonerVisitsNotificationDto(events),
        ),
      )
    }

    return notificationGroupDtos
  }

  private fun createPrisonerVisitsNotificationDto(events: MutableList<VisitNotificationEvent>): List<PrisonerVisitsNotificationDto> = events.map {
    LOG.info("createPrisonerVisitsNotificationDto Entered - created visit notification for visit with booking reference: {}", it.bookingReference)
    val visit = this.visitService.getVisitByReference(it.bookingReference)
    val actionedBy = this.visitEventAuditService.getLastUserToUpdateSlotByReference(it.bookingReference)

    PrisonerVisitsNotificationDto(
      prisonerNumber = visit.prisonerId,
      lastActionedBy = ActionedByDto(actionedBy),
      visitDate = visit.startTimestamp.toLocalDate(),
      bookingReference = it.bookingReference,
      notificationEventAttributes = it.visitNotificationEventAttributes.map { attribute -> VisitNotificationEventAttributeDto(attribute) }.toList(),
    )
  }

  fun getNotificationsTypesForBookingReference(bookingReference: String): List<NotificationEventType> = visitNotificationEventRepository.getNotificationsTypesForBookingReference(bookingReference)

  fun getNotificationEventsForBookingReference(bookingReference: String): List<VisitNotificationEventDto> = this.visitNotificationEventRepository.getVisitNotificationEventsByBookingReference(bookingReference).map { VisitNotificationEventDto(it) }

  @Transactional
  fun ignoreVisitNotifications(visitReference: String, ignoreVisitNotification: IgnoreVisitNotificationsDto): VisitDto {
    val visit = visitService.getBookedVisitByReference(visitReference)
    visitEventAuditService.saveIgnoreVisitNotificationEventAudit(ignoreVisitNotification.actionedBy, visit, ignoreVisitNotification.reason)
    deleteVisitNotificationEvents(visitReference, IGNORE_VISIT_NOTIFICATIONS, ignoreVisitNotification.reason)
    return visit
  }

  @Transactional
  fun deleteVisitNotificationEvents(visitReference: String, reason: UnFlagEventReason, text: String? = null) {
    val visitNotificationEvents = visitNotificationEventRepository.getVisitNotificationEventsByBookingReference(visitReference)

    if (visitNotificationEvents.isNotEmpty()) {
      visitNotificationEventRepository.deleteByBookingReference(visitReference)

      // after deleting the visit notifications - update application insights
      visitNotificationFlaggingService.unFlagTrackEvents(visitReference, visitNotificationEvents.map { it.type }, reason, text)
    }
  }
}
