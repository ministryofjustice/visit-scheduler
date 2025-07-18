package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestSummaryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@Service
class VisitRequestsService(
  private val visitRepository: VisitRepository,
  private val prisonerService: PrisonerService,
  private val visitEventAuditService: VisitEventAuditService,
  private val visitRequestsApprovalService: VisitRequestsApprovalService,
  private val snsService: SnsService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getVisitRequestsCountForPrison(prisonCode: String): Int {
    LOG.info("getVisitRequestsCountForPrison called with prisonCode - $prisonCode")

    return visitRepository.getCountOfRequestedVisitsForPrison(prisonCode).toInt()
  }

  @Transactional(readOnly = true)
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

  fun approveVisitRequestByReference(approveVisitRequestBodyDto: ApproveVisitRequestBodyDto): VisitDto {
    val approvalResponseDto = visitRequestsApprovalService.approveVisitRequestByReference(approveVisitRequestBodyDto)

    val snsDomainEventPublishDto = SnsDomainEventPublishDto(
      reference = approvalResponseDto.visitDto.reference,
      createdTimestamp = LocalDateTime.now(),
      modifiedTimestamp = approvalResponseDto.visitDto.modifiedTimestamp, // Not used.
      prisonerId = approvalResponseDto.visitDto.prisonerId,
      eventAuditId = approvalResponseDto.eventAuditDto.id,
    )

    snsService.sendVisitRequestActionedEvent(snsDomainEventPublishDto)

    return approvalResponseDto.visitDto
  }
}
