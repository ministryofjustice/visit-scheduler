package uk.gov.justice.digital.hmpps.visitscheduler.service

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
  @Transactional
  fun handleNonAssociations(nonAssociationChangedNotification: NonAssociationChangedNotificationDto) {
    // get the prisoners prison code
    val prisonerDetails = prisonerOffenderSearchClient.getPrisoner(nonAssociationChangedNotification.prisonerNumber)
    var overlappingVisits = listOf<VisitDto>()
    val toDate = nonAssociationChangedNotification.validToDate?.atTime(23, 59)

    if ((toDate == null) || toDate.isAfter(LocalDateTime.now())) {
      val fromDate = getNowOrFutureDateTimeAtStartOfDay(nonAssociationChangedNotification.validFromDate)
      val primaryPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.prisonerNumber, prisonerDetails?.prisonId, fromDate, toDate)
      val nonAssociationPrisonerVisits = getBookedVisits(nonAssociationChangedNotification.nonAssociationPrisonerNumber, prisonerDetails?.prisonId, fromDate, toDate)

      if (primaryPrisonerVisits.isNotEmpty() && nonAssociationPrisonerVisits.isNotEmpty()) {
        overlappingVisits = getOverLappingVisits(primaryPrisonerVisits, nonAssociationPrisonerVisits)
      }
    }

    overlappingVisits.forEach {
      val data = telemetryClientService.createFlagEventFromVisitDto(it, EventType.NON_ASSOCIATION_EVENT)
      telemetryClientService.trackEvent(TelemetryVisitEvents.FLAG_EVENT, data)
    }
  }

  private fun getNowOrFutureDateTimeAtStartOfDay(validFromDate: LocalDate): LocalDateTime {
    return if (validFromDate.isAfter(LocalDate.now())) {
      validFromDate.atStartOfDay()
    } else {
      LocalDateTime.now()
    }
  }

  private fun getOverLappingVisits(primaryPrisonerVisits: List<VisitDto>, nonAssociationPrisonerVisits: List<VisitDto>): List<VisitDto> {
    val overlappingVisits = mutableListOf<VisitDto>()
    val primaryPrisonerVisitDatesByPrison = nonAssociationPrisonerVisits.map { Pair(it.startTimestamp.toLocalDate(), it.prisonCode) }.toSet()
    val nonAssociationPrisonerVisitDatesByPrison = nonAssociationPrisonerVisits.map { Pair(it.startTimestamp.toLocalDate(), it.prisonCode) }.toSet()

    val overlappingVisitDates = primaryPrisonerVisitDatesByPrison.filter { nonAssociationPrisonerVisitDatesByPrison.contains(it) }
    overlappingVisits.addAll(primaryPrisonerVisits.filter { overlappingVisitDates.contains(Pair(it.startTimestamp.toLocalDate(), it.prisonCode)) })
    overlappingVisits.addAll(nonAssociationPrisonerVisits.filter { overlappingVisitDates.contains(Pair(it.startTimestamp.toLocalDate(), it.prisonCode)) })
    return overlappingVisits.toList()
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
