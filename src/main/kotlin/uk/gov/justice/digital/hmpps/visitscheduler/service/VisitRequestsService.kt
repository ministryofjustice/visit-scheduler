package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveVisitRequestResponseDto
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

    val prisonerInfos = prisonerService.getPrisoners(visitRequests.distinctBy { it.prisonerId }.map { it.prisonerId })

    for (visit in visitRequests) {
      // To avoid failing the entire call, if we cannot get the prisoner details, swallow the exception and use placeholder values instead of name.
      val prisoner = prisonerInfos[visit.prisonerId]

      val requestedOnDate = visitEventAuditService.findByBookingReferenceOrderById(visit.reference)
        .first { event -> event.type == EventAuditType.REQUESTED_VISIT }
        .createTimestamp
        .toLocalDate()

      val visitRequestSummaryDto = VisitRequestSummaryDto(
        visitReference = visit.reference,
        visitDate = visit.sessionSlot.slotDate,
        requestedOnDate = requestedOnDate,
        prisonerFirstName = prisoner?.firstName ?: visit.prisonerId,
        prisonerLastName = prisoner?.lastName ?: visit.prisonerId,
        prisonNumber = visit.prisonerId,
        mainContact = visit.visitContact?.name,
      )

      visitRequestSummaryList.add(visitRequestSummaryDto)
    }

    return visitRequestSummaryList.sortedBy { it.visitDate }
  }

  fun approveVisitRequestByReference(visitReference: String): ApproveVisitRequestResponseDto {
    visitRepository.approveVisitRequestForPrisonByReference(visitReference)

    val updatedVisit = visitRepository.findByReference(visitReference)!!

    // TODO: VB-4953 (Staff approves visit request):
    //  - Add new event for staff approving visit to Event Audit table (VB-5780)
    //  - Add logic to find the visit in the visit_notification_events table and un-flag any entries (VB-5787)
    //  - Add logic to raise to application insights (VB-5792)
    //  - Add new domain event and raise it (after of transaction completion) for notification service to consume and send comms (VB-5791)

    val prisoner = try {
      prisonerService.getPrisoner(updatedVisit.prisonerId)
    } catch (e: Exception) {
      LOG.error("Failed to get prisoner info while approving visit - $visitReference, continuing with placeholder info, exception - $e")
      null
    }

    return ApproveVisitRequestResponseDto(
      visitReference = visitReference,
      prisonerFirstName = prisoner?.firstName ?: updatedVisit.prisonerId,
      prisonerLastName = prisoner?.lastName ?: updatedVisit.prisonerId,
    )
  }
}
