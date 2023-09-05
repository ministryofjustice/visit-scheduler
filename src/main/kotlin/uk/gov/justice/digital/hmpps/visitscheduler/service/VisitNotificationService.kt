package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerOffenderSearchClient
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitFilter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.specification.VisitSpecification
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class VisitNotificationService(
  private val visitRepository: VisitRepository,
  private val telemetryClientService: TelemetryClientService,
  private val prisonerOffenderSearchClient: PrisonerOffenderSearchClient,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun handleNonAssociations(nonAssociationChangedNotification: NonAssociationChangedNotificationDto) {
    if (isNotificationDatesValid(nonAssociationChangedNotification)) {
      val overlappingVisits = getOverLappingVisits(nonAssociationChangedNotification)
      overlappingVisits.forEach {
        LOG.info("Flagging visit with reference {} for non association", it.reference)
        handleVisitWithNonAssociation(it)
      }
    }
  }

  private fun handleVisitWithNonAssociation(impactedVisit: VisitDto) {
    val data = telemetryClientService.createFlagEventFromVisitDto(impactedVisit, NotificationEventType.NON_ASSOCIATION_EVENT)
    telemetryClientService.trackEvent(TelemetryVisitEvents.FLAG_EVENT, data)
  }
  private fun isNotificationDatesValid(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): Boolean {
    val toDate = getValidToDateTime(nonAssociationChangedNotification)
    return (toDate == null) || toDate.isAfter(LocalDateTime.now())
  }

  private fun getOverLappingVisits(nonAssociationChangedNotification: NonAssociationChangedNotificationDto): List<VisitDto> {
    // get the prisoners prison code
    val prisonCode = prisonerOffenderSearchClient.getPrisoner(nonAssociationChangedNotification.prisonerNumber)?.prisonId
    val fromDate = getValidFromDateTime(nonAssociationChangedNotification)
    val toDate = getValidToDateTime(nonAssociationChangedNotification)

    val primaryPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.prisonerNumber, prisonCode, fromDate, toDate)
    val nonAssociationPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.nonAssociationPrisonerNumber, prisonCode, fromDate, toDate)
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
    return nonAssociationChangedNotification.validToDate?.atTime(23, 59)
  }

  fun getBookedVisits(prisonerNumber: String, prisonCode: String?, startDateTime: LocalDateTime, endDateTime: LocalDateTime?): List<VisitDto> {
    val visitFilter = VisitFilter(
      prisonerId = prisonerNumber,
      prisonCode = prisonCode,
      startDateTime = startDateTime,
      endDateTime = endDateTime,
      visitStatusList = listOf(VisitStatus.BOOKED),
    )

    return visitRepository.findAll(VisitSpecification(visitFilter)).map { VisitDto(it) }
  }
}
