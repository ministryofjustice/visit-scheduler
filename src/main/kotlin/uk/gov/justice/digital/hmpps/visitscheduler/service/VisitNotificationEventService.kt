package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_CREATED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class VisitNotificationEventService(
  private val visitService: VisitService,
  private val telemetryClientService: TelemetryClientService,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val prisonerService: PrisonerService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleNonAssociations(notificationDto: NonAssociationChangedNotificationDto) {
    with(notificationDto) {
      if (NON_ASSOCIATION_CREATED == type) {
        val affectedVisits = getOverLappingVisits(notificationDto)
        processVisitsWithNotifications(affectedVisits, NON_ASSOCIATION_EVENT)
      } else if (type in arrayOf(NON_ASSOCIATION_DELETED, NON_ASSOCIATION_CLOSED)) {
        if (!prisonerService.hasPrisonerGotANonAssociationWith(prisonerNumber, nonAssociationPrisonerNumber)) {
          val affectedNotifications = getAffectedNotifications(notificationDto)
          deleteNotificationsThatAreNoLongerValid(affectedNotifications)
        }
      }
    }
  }

  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, notificationDto.prisonCode)
      processVisitsWithNotifications(affectedVisits, NotificationEventType.PRISONER_RELEASED_EVENT)
    }
  }

  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      val prisonCode = prisonerService.getPrisonerPrisonCode(notificationDto.prisonerNumber)
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, prisonCode)
      processVisitsWithNotifications(affectedVisits, NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
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
    var reference: String? = null
    affectedVisits.forEach {
      LOG.info("Flagging visit with reference {} for non association", it.reference)
      if (!visitNotificationEventRepository.isEventARecentDuplicate(it.reference, type)) {
        val bookingEventAudit = visitService.getLastEventForBooking(it.reference)
        val data = telemetryClientService.createFlagEventFromVisitDto(it, bookingEventAudit, type)
        telemetryClientService.trackEvent(TelemetryVisitEvents.FLAGGED_VISIT_EVENT, data)
        reference = saveVisitNotification(it, reference)
      }
    }
  }

  private fun deleteNotificationsThatAreNoLongerValid(visitNotificationEvents: List<VisitNotificationEvent>) {
    visitNotificationEventRepository.deleteAll(visitNotificationEvents)
  }

  private fun saveVisitNotification(
    impactedVisit: VisitDto,
    reference: String?,
  ): String {
    val savedVisitNotificationEvent = visitNotificationEventRepository.saveAndFlush(
      if (reference == null) {
        VisitNotificationEvent(
          impactedVisit.reference,
          NON_ASSOCIATION_EVENT,
        )
      } else {
        VisitNotificationEvent(
          impactedVisit.reference,
          NON_ASSOCIATION_EVENT,
          _reference = reference,
        )
      },
    )
    return savedVisitNotificationEvent.reference
  }

  private fun getOverLappingVisits(notificationDto: NonAssociationChangedNotificationDto): List<VisitDto> {
    // get the prisoners' prison code
    val prisonCode = getPrisonCode(notificationDto.prisonerNumber)
    val fromDate = LocalDate.now().atStartOfDay()

    val primaryPrisonerVisits = visitService.getBookedVisits(notificationDto.prisonerNumber, prisonCode, fromDate)
    val nonAssociationPrisonerVisits = visitService.getBookedVisits(notificationDto.nonAssociationPrisonerNumber, prisonCode, fromDate)
    return getOverLappingVisits(primaryPrisonerVisits, nonAssociationPrisonerVisits)
  }

  private fun getAffectedNotifications(notificationDto: NonAssociationChangedNotificationDto): List<VisitNotificationEvent> {
    // get the prisoners' prison code
    val prisonCode = getPrisonCode(notificationDto.prisonerNumber)

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

  private fun getPrisonCode(prisonerNumber: String): String {
    return prisonerService.getPrisonerPrisonCode(prisonerNumber)
      ?: throw ItemNotFoundException("Prison can not be found for prisoner: $prisonerNumber")
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
}
