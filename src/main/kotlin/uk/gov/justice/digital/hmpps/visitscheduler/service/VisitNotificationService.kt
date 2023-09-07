package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class VisitNotificationService(
  private val visitRepository: VisitRepository,
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
        handleVisitWithNonAssociation(it)
      }
    }
  }

  private fun isNotADeleteEvent(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): Boolean {
    val nonAssociations = prisonerService.getOffenderNonAssociationList(nonAssociationChangedNotification.prisonerNumber)
    return nonAssociations.any { it.offenderNonAssociation.offenderNo == nonAssociationChangedNotification.nonAssociationPrisonerNumber }
  }

  private fun handleVisitWithNonAssociation(impactedVisit: Visit) {
    if (!visitNotificationEventRepository.isEventARecentDuplicate(impactedVisit.id, NotificationEventType.NON_ASSOCIATION_EVENT)) {
      val data =
        telemetryClientService.createFlagEventFromVisitDto(impactedVisit, NotificationEventType.NON_ASSOCIATION_EVENT)
      telemetryClientService.trackEvent(TelemetryVisitEvents.FLAGGED_VISIT_EVENT, data)
      visitNotificationEventRepository.saveAndFlush(
        VisitNotificationEvent(
          impactedVisit.id,
          NotificationEventType.NON_ASSOCIATION_EVENT,
        ),
      )
    }
  }

  private fun isNotificationDatesValid(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): Boolean {
    val toDate = getValidToDateTime(nonAssociationChangedNotification)
    return (toDate == null) || toDate.isAfter(LocalDateTime.now())
  }

  private fun getOverLappingVisits(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): List<Visit> {
    // get the prisoners' prison code
    val prisonCode = prisonerOffenderSearchClient.getPrisoner(nonAssociationChangedNotification.prisonerNumber)?.prisonId
    val fromDate = getValidFromDateTime(nonAssociationChangedNotification)
    val toDate = getValidToDateTime(nonAssociationChangedNotification)

    val primaryPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.prisonerNumber, prisonCode, fromDate, toDate)
    val nonAssociationPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.nonAssociationPrisonerNumber, prisonCode, fromDate, toDate)
    return getOverLappingVisits(primaryPrisonerVisits, nonAssociationPrisonerVisits)
  }

  private fun getOverLappingVisits(primaryPrisonerVisits: List<Visit>, nonAssociationPrisonerVisits: List<Visit>): List<Visit> {
    val overlappingVisits = mutableListOf<Visit>()
    if (primaryPrisonerVisits.isNotEmpty() && nonAssociationPrisonerVisits.isNotEmpty()) {
      val overlappingVisitDates = getOverlappingVisitDatesByPrison(primaryPrisonerVisits, nonAssociationPrisonerVisits)
      if (overlappingVisitDates.isNotEmpty()) {
        overlappingVisits.addAll(getVisitsForDateAndPrison(primaryPrisonerVisits, overlappingVisitDates))
        overlappingVisits.addAll(getVisitsForDateAndPrison(nonAssociationPrisonerVisits, overlappingVisitDates))
      }
    }

    return overlappingVisits.toList()
  }

  private fun getVisitsForDateAndPrison(visits: List<Visit>, visitDatesByPrison: List<Pair<LocalDate, Long>>): List<Visit> {
    return visits.filter {
      visitDatesByPrison.contains(Pair(it.visitStart.toLocalDate(), it.prisonId))
    }
  }

  private fun getOverlappingVisitDatesByPrison(primaryPrisonerVisits: List<Visit>, nonAssociationPrisonerVisits: List<Visit>): List<Pair<LocalDate, Long>> {
    // all visits by date and prison for first prisoner
    val primaryPrisonerVisitDatesByPrison = primaryPrisonerVisits.map { Pair(it.visitStart.toLocalDate(), it.prisonId) }.toSet()
    // all visits by date and prison for second prisoner
    val nonAssociationPrisonerVisitDatesByPrison = nonAssociationPrisonerVisits.map { Pair(it.visitStart.toLocalDate(), it.prisonId) }.toSet()

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

  fun getBookedVisits(prisonerNumber: String, prisonCode: String?, startDateTime: LocalDateTime, endDateTime: LocalDateTime?): List<Visit> {
    val visitFilter = VisitFilter(
      prisonerId = prisonerNumber,
      prisonCode = prisonCode,
      startDateTime = startDateTime,
      endDateTime = endDateTime,
      visitStatusList = listOf(VisitStatus.BOOKED),
    )

    return visitRepository.findAll(VisitSpecification(visitFilter))
  }
}
