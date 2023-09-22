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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
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
  fun handleNonAssociations(nonAssociationChangedNotification: NonAssociationChangedNotificationDto) {
    if (isNotificationDatesValid(nonAssociationChangedNotification) && isNotADeleteEvent(nonAssociationChangedNotification)) {
      val overlappingVisits = getOverLappingVisits(nonAssociationChangedNotification)
      overlappingVisits.forEach {
        LOG.info("Flagging visit with reference {} for non association", it.reference)
        handleVisitWithNonAssociationNotification(it)
      }
    }
  }

  private fun isNotADeleteEvent(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): Boolean {
    try {
      val isMatch: Predicate<OffenderNonAssociationDetailDto> = Predicate {
        (
          it.offenderNonAssociation.offenderNo == nonAssociationChangedNotification.nonAssociationPrisonerNumber &&
            it.effectiveDate == nonAssociationChangedNotification.validFromDate &&
            it.expiryDate == nonAssociationChangedNotification.validToDate
          )
      }

      val nonAssociations =
        prisonerService.getOffenderNonAssociationList(nonAssociationChangedNotification.prisonerNumber)
      return nonAssociations.any { isMatch.test(it) }
    } catch (e: Exception) {
      LOG.error("isNotADeleteEvent: failed, This could be a delete notification ", e)
      return true
    }
  }

  private fun handleVisitWithNonAssociationNotification(impactedVisit: VisitDto) {
    if (!visitNotificationEventRepository.isEventARecentDuplicate(impactedVisit.reference, NotificationEventType.NON_ASSOCIATION_EVENT)) {
      val bookingEventAudit = visitService.getLastEventForBooking(impactedVisit.reference)
      val data =
        telemetryClientService.createFlagEventFromVisitDto(impactedVisit, bookingEventAudit, NotificationEventType.NON_ASSOCIATION_EVENT)
      telemetryClientService.trackEvent(TelemetryVisitEvents.FLAGGED_VISIT_EVENT, data)
      visitNotificationEventRepository.saveAndFlush(
        VisitNotificationEvent(
          impactedVisit.reference,
          NotificationEventType.NON_ASSOCIATION_EVENT,
        ),
      )
    }
  }

  fun handlePersonRestrictionChangeNotification(personRestrictionChangeNotificationDto: PersonRestrictionChangeNotificationDto) {
    // TODO not yet implemented
  }

  fun handlePrisonerReceivedNotification(dto: PrisonerReceivedNotificationDto) {
    // TODO not yet implemented
  }

  fun handlePrisonerReleasedNotification(dto: PrisonerReleasedNotificationDto) {
    // TODO not yet implemented
  }

  fun handlePrisonerRestrictionChangeNotification(dto: PrisonerRestrictionChangeNotificationDto) {
    // TODO not yet implemented
  }

  fun handleVisitorRestrictionChangeNotification(dto: VisitorRestrictionChangeNotificationDto) {
    // TODO not yet implemented
  }

  private fun isNotificationDatesValid(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): Boolean {
    val toDate = getValidToDateTime(nonAssociationChangedNotification)
    return (toDate == null) || toDate.isAfter(LocalDateTime.now())
  }

  private fun getOverLappingVisits(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): List<VisitDto> {
    // get the prisoners' prison code
    val prisonCode = prisonerOffenderSearchClient.getPrisoner(nonAssociationChangedNotification.prisonerNumber)?.prisonId
    val fromDate = getValidFromDateTime(nonAssociationChangedNotification)
    val toDate = getValidToDateTime(nonAssociationChangedNotification)

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

  private fun getValidFromDateTime(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): LocalDateTime {
    return if (nonAssociationChangedNotification.validFromDate.isAfter(LocalDate.now())) {
      nonAssociationChangedNotification.validFromDate.atStartOfDay()
    } else {
      LocalDateTime.now()
    }
  }

  private fun getValidToDateTime(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): LocalDateTime? {
    return nonAssociationChangedNotification.validToDate?.let { LocalDateTime.of(nonAssociationChangedNotification.validToDate, LocalTime.MAX) }
  }

}
