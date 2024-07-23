package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerSupportedAlertCodeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionDeletedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertCreatedUpdatedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerVisitsNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
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
  private val prisonerContactRegistryClient: PrisonerContactRegistryClient,
) {

  @Lazy
  @Autowired
  private lateinit var visitEventAuditService: VisitEventAuditService

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleNonAssociations(notificationDto: NonAssociationChangedNotificationDto) {
    LOG.debug("NonAssociations notification received : $notificationDto")
    if (NON_ASSOCIATION_CREATED == notificationDto.type) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
      prisonCode?.let {
        val affectedVisits = getOverLappingVisits(notificationDto, prisonCode)

        processVisitsWithNotifications(affectedVisits, NON_ASSOCIATION_EVENT, null)
      }
    } else if (notificationDto.type in arrayOf(NON_ASSOCIATION_DELETED, NON_ASSOCIATION_CLOSED)) {
      if (!prisonerService.hasPrisonerGotANonAssociationWith(notificationDto.prisonerNumber, notificationDto.nonAssociationPrisonerNumber)) {
        val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
        prisonCode?.let {
          val affectedNotifications = getAffectedNotifications(notificationDto, it)
          deleteNotificationsThatAreNoLongerValid(affectedNotifications, NON_ASSOCIATION_EVENT, UnFlagEventReason.NON_ASSOCIATION_REMOVED)
        }
      }
    }
  }

  @Transactional
  fun handleAddPrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    LOG.debug("PrisonVisitBlockDate notification received : $prisonDateBlockedDto")
    val affectedVisits = visitService.getBookedVisitsForDate(prisonCode = prisonDateBlockedDto.prisonCode, date = prisonDateBlockedDto.visitDate)

    processVisitsWithNotifications(affectedVisits, PRISON_VISITS_BLOCKED_FOR_DATE, null)
  }

  @Transactional
  fun handleRemovePrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    LOG.debug("RemovePrisonVisitBlockDate notification received : $prisonDateBlockedDto")
    val affectedNotifications = visitNotificationEventRepository.getEventsByVisitDate(
      prisonDateBlockedDto.prisonCode,
      prisonDateBlockedDto.visitDate,
      PRISON_VISITS_BLOCKED_FOR_DATE,
    )
    deleteNotificationsThatAreNoLongerValid(affectedNotifications, PRISON_VISITS_BLOCKED_FOR_DATE, UnFlagEventReason.PRISON_EXCLUDE_DATE_REMOVED)
  }

  @Transactional
  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    LOG.debug("PrisonerReleasedNotification notification received : $notificationDto")
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, notificationDto.prisonCode)

      processVisitsWithNotifications(affectedVisits, PRISONER_RELEASED_EVENT, null)
    }
  }

  @Transactional
  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    LOG.debug("PrisonerRestrictionChange notification received")
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)

      val startDateTime = (if (LocalDate.now() > notificationDto.validFromDate) LocalDate.now() else notificationDto.validFromDate).atStartOfDay()
      val endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX)
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, prisonCode, startDateTime, endDateTime)

      processVisitsWithNotifications(affectedVisits, PRISONER_RESTRICTION_CHANGE_EVENT, null)
    }
  }

  @Transactional
  fun handlePrisonerAlertCreatedUpdatedNotification(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("handlePrisonerAlertCreatedUpdated notification received")

    if (notificationDto.alertsAdded.isNotEmpty()) {
      processAlertsAdded(notificationDto)
    }
    if (notificationDto.alertsRemoved.isNotEmpty()) {
      processAlertsRemoved(notificationDto)
    }
  }

  private fun processAlertsAdded(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("Entered handlePrisonerAlertCreatedUpdated processAlertsAdded")

    val prisonerSupportedAlertCodes = PrisonerSupportedAlertCodeType.entries.map { it.name }.toSet()

    val prisonerSupportedAlertsAdded = notificationDto.alertsAdded.filter { code -> code in prisonerSupportedAlertCodes }
    if (prisonerSupportedAlertsAdded.isNotEmpty()) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, prisonCode)

      processVisitsWithNotifications(affectedVisits, PRISONER_ALERTS_UPDATED_EVENT, notificationDto.description)
    }
  }

  private fun processAlertsRemoved(notificationDto: PrisonerAlertCreatedUpdatedNotificationDto) {
    LOG.debug("Entered handlePrisonerAlertCreatedUpdated processAlertsRemoved")

    val prisonerSupportedAlertCodes = PrisonerSupportedAlertCodeType.entries.map { it.name }.toSet()

    val prisonerSupportedAlertsRemoved = notificationDto.alertsRemoved.filter { it in prisonerSupportedAlertCodes }
    if (prisonerSupportedAlertsRemoved.isNotEmpty()) {
      val prisonerDetails = prisonerService.getPrisoner(notificationDto.prisonerNumber)
      prisonerDetails?.let { prisoner ->
        val prisonerActiveAlertCodes = prisoner.alerts.filter { it.active }.map { it.alertCode }
        if (!prisonerActiveAlertCodes.any { it in prisonerSupportedAlertCodes }) {
          prisoner.prisonCode?.let {
            val currentPrisonNotifications = visitNotificationEventRepository.getEventsBy(notificationDto.prisonerNumber, prisoner.prisonCode!!, PRISONER_ALERTS_UPDATED_EVENT)
            deleteNotificationsThatAreNoLongerValid(currentPrisonNotifications, PRISONER_ALERTS_UPDATED_EVENT, UnFlagEventReason.PRISONER_ALERT_CODE_REMOVED)
          }
        }
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
      val allAffectedVisits = visitService.getFutureVisitsByVisitorId(
        visitorId = notificationDto.visitorId,
        prisonerId = notificationDto.prisonerNumber,
        endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX),
      )
      if (allAffectedVisits.isNotEmpty()) {
        val description = "visitor ${notificationDto.visitorId} has restriction upserted - ${notificationDto.restrictionType} for prisoner ${notificationDto.prisonerNumber}"
        processVisitsWithNotifications(allAffectedVisits, PERSON_RESTRICTION_UPSERTED_EVENT, description)
      }
    }
  }

  @Transactional
  fun handlePersonRestrictionDeletedNotification(notificationDto: PersonRestrictionDeletedNotificationDto) {
    LOG.debug("PersonRestrictionDeletedNotificationDto notification received : {}", notificationDto)

    val visitorSupportedRestrictionTypes = VisitorSupportedRestrictionType.entries.map { it.name }.toSet()

    if (visitorSupportedRestrictionTypes.contains(notificationDto.restrictionType)) {
      val personActiveRestrictionsDto = prisonerContactRegistryClient.getVisitorActiveRestrictions(notificationDto.prisonerNumber, notificationDto.visitorId)
      if (!personActiveRestrictionsDto.activeRestrictions.any { it in visitorSupportedRestrictionTypes }) {
        val currentFlaggedNotifications = visitNotificationEventRepository.getEventsByVisitorId(notificationDto.prisonerNumber, notificationDto.visitorId.toLong(), PERSON_RESTRICTION_UPSERTED_EVENT)
        deleteNotificationsThatAreNoLongerValid(currentFlaggedNotifications, PERSON_RESTRICTION_UPSERTED_EVENT, UnFlagEventReason.VISITOR_RESTRICTION_REMOVED)
      }
    }
  }

  @Transactional
  fun handleVisitorRestrictionChangeNotification(notificationDto: VisitorRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      // TODO not yet implemented
    }
  }

  @Transactional
  fun handlePrisonerReceivedNotification(notificationDto: PrisonerReceivedNotificationDto) {
    LOG.debug("PrisonerReceivedNotification notification received : {}", notificationDto)
    if (PrisonerReceivedReasonType.TRANSFERRED == notificationDto.reason) {
      // First flag visits from all prisons excluding the one the prisoner has moved to.
      val allAffectedVisits = visitService.getFutureBookedVisitsExcludingPrison(notificationDto.prisonerNumber, notificationDto.prisonCode)
      if (allAffectedVisits.isNotEmpty()) {
        processVisitsWithNotifications(allAffectedVisits, PRISONER_RECEIVED_EVENT, null)
      }

      // Second un-flag visits from current prison if any are flagged, as they are now at this prison.
      val currentPrisonNotifications = visitNotificationEventRepository.getEventsBy(notificationDto.prisonerNumber, notificationDto.prisonCode, PRISONER_RECEIVED_EVENT)
      deleteNotificationsThatAreNoLongerValid(currentPrisonNotifications, PRISONER_RECEIVED_EVENT, UnFlagEventReason.PRISONER_RETURNED_TO_PRISON)
    }
  }

  private fun processVisitsWithNotifications(affectedVisits: List<VisitDto>, type: NotificationEventType, description: String?) {
    val affectedVisitsNoDuplicate = affectedVisits.filter { !visitNotificationEventRepository.isEventARecentDuplicate(it.reference, type) }

    affectedVisitsNoDuplicate.forEach {
      val bookingEventAudit = visitEventAuditService.getLastEventForBooking(it.reference)
      visitNotificationFlaggingService.flagTrackEvents(it, bookingEventAudit, type)
    }

    if (isPairGroupRequired(type)) {
      val affectedPairedVisits = pairWithEachOther(affectedVisits)
      affectedPairedVisits.forEach {
        if (!visitNotificationEventRepository.isEventARecentPairedDuplicate(it.first.reference, it.second.reference, type)) {
          saveGroupedVisitsNotification(it.toList(), type)
        }
      }
    } else {
      saveVisitsNotification(affectedVisitsNoDuplicate, type, description)
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
      reference = saveVisitNotification(it, reference, type, null)
    }
  }

  private fun saveVisitsNotification(
    affectedVisitsNoDuplicate: List<VisitDto>,
    type: NotificationEventType,
    description: String?,
  ) {
    affectedVisitsNoDuplicate.forEach {
      saveVisitNotification(it, null, type, description)
    }
  }

  private fun deleteNotificationsThatAreNoLongerValid(
    visitNotificationEvents: List<VisitNotificationEvent>,
    notificationEventType: NotificationEventType? = null,
    reason: UnFlagEventReason,
  ) {
    visitNotificationEvents.forEach {
      visitNotificationFlaggingService.unFlagTrackEvents(it.bookingReference, notificationEventType, reason, null)
    }
    visitNotificationEventRepository.deleteAll(visitNotificationEvents)
  }

  private fun saveVisitNotification(
    impactedVisit: VisitDto,
    reference: String?,
    type: NotificationEventType,
    description: String?,
  ): String {
    val savedVisitNotificationEvent = visitNotificationEventRepository.saveAndFlush(
      if (reference == null) {
        VisitNotificationEvent(
          impactedVisit.reference,
          type,
          description,
        )
      } else {
        VisitNotificationEvent(
          impactedVisit.reference,
          type,
          description,
          _reference = reference,
        )
      },
    )

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

    val prisonerEventsToBeDeleted = prisonersNotifications.filter {
        prisonersNotify ->
      nonAssociationPrisonersNotifications.any { it.reference == prisonersNotify.reference }
    }
    val nsPrisonerEventsToBeDeleted = nonAssociationPrisonersNotifications.filter {
        nsPrisonersNotify ->
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

  private fun getVisitsForDateAndPrison(visits: List<VisitDto>, visitDatesByPrison: List<Pair<LocalDate, String>>): List<VisitDto> {
    return visits.filter {
      visitDatesByPrison.contains(Pair(it.startTimestamp.toLocalDate(), it.prisonCode))
    }
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

  private fun getValidToDateTime(validToDate: LocalDate?): LocalDateTime? {
    return validToDate?.let { LocalDateTime.of(validToDate, LocalTime.MAX) }
  }

  fun getNotificationCountForPrison(prisonCode: String): Int {
    return this.visitNotificationEventRepository.getNotificationGroupsCountByPrisonCode(prisonCode) ?: 0
  }

  fun getNotificationCount(): Int {
    return this.visitNotificationEventRepository.getNotificationGroupsCount() ?: 0
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

  private fun createPrisonerVisitsNotificationDto(events: MutableList<VisitNotificationEvent>): List<PrisonerVisitsNotificationDto> {
    return events.map {
      val visit = this.visitService.getVisitByReference(it.bookingReference)
      val bookedByUserName = this.visitEventAuditService.getLastUserToUpdateSlotByReference(it.bookingReference)

      PrisonerVisitsNotificationDto(
        prisonerNumber = visit.prisonerId,
        bookedByUserName = bookedByUserName,
        visitDate = visit.startTimestamp.toLocalDate(),
        bookingReference = it.bookingReference,
      )
    }
  }

  fun getNotificationsTypesForBookingReference(bookingReference: String): List<NotificationEventType> {
    return this.visitNotificationEventRepository.getNotificationsTypesForBookingReference(bookingReference)
  }

  @Transactional
  fun ignoreVisitNotifications(visitReference: String, ignoreVisitNotification: IgnoreVisitNotificationsDto): VisitDto {
    val visit = visitService.getBookedVisitByReference(visitReference)
    visitEventAuditService.saveIgnoreVisitNotificationEventAudit(ignoreVisitNotification.actionedBy, visit, ignoreVisitNotification.reason)
    deleteVisitNotificationEvents(visitReference, null, UnFlagEventReason.IGNORE_VISIT_NOTIFICATIONS, ignoreVisitNotification.reason)
    return visit
  }

  @Transactional
  fun deleteVisitNotificationEvents(visitReference: String, type: NotificationEventType?, reason: UnFlagEventReason, text: String? = null) {
    type?.let {
      visitNotificationEventRepository.deleteByBookingReferenceAndType(visitReference, it)
    } ?: visitNotificationEventRepository.deleteByBookingReference(visitReference)

    // after deleting the visit notifications - update application insights
    visitNotificationFlaggingService.unFlagTrackEvents(visitReference, type, reason, text)
  }
}
