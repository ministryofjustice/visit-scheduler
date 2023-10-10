package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.OffenderNonAssociationDetailDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.function.Predicate

@Service
class VisitNotificationEventService(
  private val visitService: VisitService,
  private val telemetryClientService: TelemetryClientService,
  private val prisonerOffenderSearchClient: PrisonerOffenderSearchClient,
  private val visitNotificationEventRepository: VisitNotificationEventRepository,
  private val prisonerService: PrisonerService,
) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleNonAssociations(notificationDto: NonAssociationChangedNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate) && isNotADeleteEvent(notificationDto)) {
      val affectedVisits = getOverLappingVisits(notificationDto)
      processVisitsWithNotifications(affectedVisits, NON_ASSOCIATION_EVENT)
    }
  }

  private fun isNotADeleteEvent(notificationDto: NonAssociationChangedNotificationDto): Boolean {
    try {
      val isMatch: Predicate<OffenderNonAssociationDetailDto> = Predicate {
        (
          it.offenderNonAssociation.offenderNo == notificationDto.nonAssociationPrisonerNumber &&
            it.effectiveDate == notificationDto.validFromDate &&
            it.expiryDate == notificationDto.validToDate
          )
      }

      val nonAssociations =
        prisonerService.getOffenderNonAssociationList(notificationDto.prisonerNumber)
      return nonAssociations.any { isMatch.test(it) }
    } catch (e: Exception) {
      LOG.error("isNotADeleteEvent: failed, This could be a delete notification ", e)
      return true
    }
  }

  fun handlePrisonerReleasedNotification(notificationDto: PrisonerReleasedNotificationDto) {
    if (RELEASED == notificationDto.reasonType) {
      val affectedVisits = visitService.getFutureVisitsBy(notificationDto.prisonerNumber, notificationDto.prisonCode)
      processVisitsWithNotifications(affectedVisits, NotificationEventType.PRISONER_RELEASED_EVENT)
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

  fun handlePrisonerRestrictionChangeNotification(notificationDto: PrisonerRestrictionChangeNotificationDto) {
    if (isNotificationDatesValid(notificationDto.validToDate)) {
      // TODO not yet implemented
    }
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

  private fun isNotificationDatesValid(validToDate: LocalDate?): Boolean {
    val toDate = getValidToDateTime(validToDate)
    return (toDate == null) || toDate.isAfter(LocalDateTime.now())
  }

  private fun getOverLappingVisits(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): List<VisitDto> {
    // get the prisoners' prison code
    val prisonCode = prisonerOffenderSearchClient.getPrisoner(nonAssociationChangedNotification.prisonerNumber)?.prisonId
    val fromDate = getValidFromDateTime(nonAssociationChangedNotification.validFromDate)
    val toDate = getValidToDateTime(nonAssociationChangedNotification.validToDate)

    val primaryPrisonerVisits = visitService.getBookedVisits(nonAssociationChangedNotification.prisonerNumber, prisonCode, fromDate, toDate)
    val nonAssociationPrisonerVisits = visitService.getBookedVisits(nonAssociationChangedNotification.nonAssociationPrisonerNumber, prisonCode, fromDate, toDate)
    return getOverLappingVisits(primaryPrisonerVisits, nonAssociationPrisonerVisits)
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

  private fun getValidFromDateTime(validFromDate: LocalDate): LocalDateTime {
    return if (validFromDate.isAfter(LocalDate.now())) {
      validFromDate.atStartOfDay()
    } else {
      LocalDateTime.now()
    }
  }

  private fun getValidToDateTime(validToDate: LocalDate?): LocalDateTime? {
    return validToDate?.let { LocalDateTime.of(validToDate, LocalTime.MAX) }
  }
}
