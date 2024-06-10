package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerVisitsNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
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

  @Autowired
  private lateinit var eventAuditRepository: EventAuditRepository

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
        processVisitsWithNotifications(affectedVisits, NON_ASSOCIATION_EVENT)
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
    val affectedVisits = visitService.getBookedVisitsForDate(
      prisonCode = prisonDateBlockedDto.prisonCode,
      date = prisonDateBlockedDto.visitDate,
    )
    processVisitsWithNotifications(affectedVisits, PRISON_VISITS_BLOCKED_FOR_DATE)
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

  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    LOG.debug("PrisonerReleasedNotification notification received : $notificationDto")
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, notificationDto.prisonCode)
      processVisitsWithNotifications(affectedVisits, PRISONER_RELEASED_EVENT)
    }
  }

  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    LOG.debug("PrisonerRestrictionChange notification received")
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)

      val startDateTime = (if (LocalDate.now() > notificationDto.validFromDate) LocalDate.now() else notificationDto.validFromDate).atStartOfDay()
      val endDateTime = notificationDto.validToDate?.atTime(LocalTime.MAX)
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, prisonCode, startDateTime, endDateTime)
      processVisitsWithNotifications(affectedVisits, PRISONER_RESTRICTION_CHANGE_EVENT)
    }
  }

  fun handlePersonRestrictionChangeNotification(notificationDto: PersonRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      // TODO not yet implemented
    }
  }

  fun handleVisitorRestrictionChangeNotification(notificationDto: VisitorRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      // TODO not yet implemented
    }
  }

  fun handlePrisonerReceivedNotification(notificationDto: PrisonerReceivedNotificationDto) {
    LOG.debug("PrisonerReceivedNotification notification received : {}", notificationDto)
    if (PrisonerReceivedReasonType.TRANSFERRED == notificationDto.reason) {
      val prisonerDetails = prisonerService.getPrisoner(notificationDto.prisonerNumber)
      prisonerDetails?.let {
        LOG.debug("PrisonerDetails received back from getPrisoner: {{}}", prisonerDetails)
        // First flag visits from previous prison as prisoner has moved if they have any
        prisonerDetails.lastPrisonCode?.let {
          val previousPrisonAffectedVisits = visitService.getFutureVisitsBy(prisonerDetails.prisonerId, prisonerDetails.lastPrisonCode)
          if (previousPrisonAffectedVisits.isNotEmpty()) {
            processVisitsWithNotifications(previousPrisonAffectedVisits, PRISONER_RECEIVED_EVENT)
          }
        }

        // Second un-flag visits from current prison if prisoner has transferred back to somewhere they've previously been
        prisonerDetails.prisonCode?.let {
          val affectedNotifications = visitNotificationEventRepository.getEventsBy(prisonerDetails.prisonerId, prisonerDetails.prisonCode!!, PRISONER_RECEIVED_EVENT)
          deleteNotificationsThatAreNoLongerValid(affectedNotifications, PRISONER_RECEIVED_EVENT, UnFlagEventReason.PRISONER_RETURNED_TO_PRISON)
        }
      } ?: run {
        LOG.debug("PrisonerDetailed received are null from getPrisoner call -- skipping flagging / unflagging of visits")
      }
    }
  }

  private fun processVisitsWithNotifications(affectedVisits: List<VisitDto>, type: NotificationEventType) {
    val affectedVisitsNoDuplicate = affectedVisits.filter { !visitNotificationEventRepository.isEventARecentDuplicate(it.reference, type) }

    affectedVisitsNoDuplicate.forEach {
      val bookingEventAudit = visitService.getLastEventForBooking(it.reference)
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
      saveVisitsNotification(affectedVisitsNoDuplicate, type)
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
    affectedVisitsNoDuplicate: List<VisitDto>,
    type: NotificationEventType,
  ) {
    affectedVisitsNoDuplicate.forEach {
      saveVisitNotification(it, null, type)
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
  ): String {
    val savedVisitNotificationEvent = visitNotificationEventRepository.saveAndFlush(
      if (reference == null) {
        VisitNotificationEvent(
          impactedVisit.reference,
          type,
        )
      } else {
        VisitNotificationEvent(
          impactedVisit.reference,
          type,
          _reference = reference,
        )
      },
    )

    eventAuditRepository.saveAndFlush(
      EventAudit(
        actionedBy = "NOT_KNOWN",
        bookingReference = impactedVisit.reference,
        applicationReference = impactedVisit.applicationReference,
        sessionTemplateReference = impactedVisit.sessionTemplateReference,
        type = EventAuditType.valueOf(type.name),
        applicationMethodType = NOT_KNOWN,
        text = null,
        userType = impactedVisit.userType,
      ),
    )

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
      val bookedByUserName = this.visitService.getLastUserNameToUpdateToSlotByReference(it.bookingReference)

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
    visitService.addEventAudit(ignoreVisitNotification.actionedBy, visit, EventAuditType.IGNORE_VISIT_NOTIFICATIONS_EVENT, ApplicationMethodType.NOT_APPLICABLE, ignoreVisitNotification.reason)
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
