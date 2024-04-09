package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NotificationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerVisitsNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.EventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
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
    if (NON_ASSOCIATION_CREATED == notificationDto.type) {
      val prisonCode = prisonerService.getPrisonerSupportedPrisonCode(notificationDto.prisonerNumber)
      prisonCode?.let {
        val affectedVisits = getOverLappingVisits(notificationDto, prisonCode)
        processVisitsWithNotifications(affectedVisits, NON_ASSOCIATION_EVENT)
      }
    } else if (notificationDto.type in arrayOf(NON_ASSOCIATION_DELETED, NON_ASSOCIATION_CLOSED)) {
      if (!prisonerService.hasPrisonerGotANonAssociationWith(notificationDto.prisonerNumber, notificationDto.nonAssociationPrisonerNumber)) {
        val prisonCode = prisonerService.getPrisonerSupportedPrisonCode(notificationDto.prisonerNumber)
        prisonCode?.let {
          val affectedNotifications = getAffectedNotifications(notificationDto, it)
          deleteNotificationsThatAreNoLongerValid(affectedNotifications, NON_ASSOCIATION_EVENT, UnFlagEventReason.NON_ASSOCIATION_REMOVED)
        }
      }
    }
  }

  @Transactional
  fun handleAddPrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    val affectedVisits = visitService.getBookedVisitsForDate(
      prisonCode = prisonDateBlockedDto.prisonCode,
      date = prisonDateBlockedDto.visitDate,
    )
    processVisitsWithNotifications(affectedVisits, PRISON_VISITS_BLOCKED_FOR_DATE)
  }

  @Transactional
  fun handleRemovePrisonVisitBlockDate(prisonDateBlockedDto: PrisonDateBlockedDto) {
    val affectedNotifications = visitNotificationEventRepository.getEventsByVisitDate(
      prisonDateBlockedDto.prisonCode,
      prisonDateBlockedDto.visitDate,
      PRISON_VISITS_BLOCKED_FOR_DATE,
    )
    deleteNotificationsThatAreNoLongerValid(affectedNotifications, PRISON_VISITS_BLOCKED_FOR_DATE, UnFlagEventReason.PRISON_EXCLUDE_DATE_REMOVED)
  }

  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, notificationDto.prisonCode)
      processVisitsWithNotifications(affectedVisits, PRISONER_RELEASED_EVENT)
    }
  }

  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      val prisonCode = prisonerService.getPrisonerSupportedPrisonCode(notificationDto.prisonerNumber)

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

  fun handlePrisonerReceivedNotification(notificationDto: PrisonerReceivedNotificationDto) {
    // TODO not yet implemented
  }

  fun handleVisitorRestrictionChangeNotification(notificationDto: VisitorRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      // TODO not yet implemented
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
