package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestSummaryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Service
@Transactional
class VisitRequestsService(
  private val visitRepository: VisitRepository,
  private val prisonerService: PrisonerService,
  private val visitEventAuditService: VisitEventAuditService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getVisitRequestsCountForPrison(prisonCode: String): Int {
    LOG.info("getVisitRequestsCountForPrison called with prisonCode - $prisonCode")

    return visitRepository.getCountOfRequestedVisitsForPrison(prisonCode).toInt()
  }

  fun getVisitRequestsForPrison(prisonCode: String): List<VisitRequestSummaryDto> {
    val visitRequests = visitRepository.getRequestedVisitsForPrison(prisonCode)

    val visitRequestSummaryList = mutableListOf<VisitRequestSummaryDto>()

    for (visit in visitRequests) {
      val prisoner = prisonerService.getPrisoner(visit.prisonerId)
      if (prisoner == null) {
        throw IllegalStateException("Prisoner with prisonerId ${visit.prisonerId} not found")
      }

      val requestedOnDate = visitEventAuditService.findByBookingReferenceOrderById(visit.reference)
        .first { event -> event.type == EventAuditType.REQUESTED_VISIT }
        .createTimestamp
        .toLocalDate()

      val visitRequestSummaryDto = VisitRequestSummaryDto(
        visitReference = visit.reference,
        visitDate = visit.sessionSlot.slotDate,
        requestedOnDate = requestedOnDate,
        prisonerName = (prisoner.firstName + " " + prisoner.lastName),
        prisonNumber = visit.prisonerId,
        mainContact = visit.visitContact?.name,
      )

      visitRequestSummaryList.add(visitRequestSummaryDto)
    }

    return visitRequestSummaryList
  }
}
