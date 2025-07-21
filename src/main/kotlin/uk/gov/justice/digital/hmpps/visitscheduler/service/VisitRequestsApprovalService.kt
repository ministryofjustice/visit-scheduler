package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestApprovalResponseDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Transactional
@Service
class VisitRequestsApprovalService(
  private val visitRepository: VisitRepository,
  private val visitEventAuditService: VisitEventAuditService,
  private val visitDtoBuilder: VisitDtoBuilder,
  private val visitNotificationEventService: VisitNotificationEventService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun approveVisitRequestByReference(approveVisitRequestBodyDto: ApproveVisitRequestBodyDto): VisitRequestApprovalResponseDto {
    val visitReference = approveVisitRequestBodyDto.visitReference

    LOG.info("approveVisitRequestByReference - called for visit - ${approveVisitRequestBodyDto.visitReference}")
    val success = visitRepository.approveVisitRequestForPrisonByReference(visitReference) > 0
    if (!success) {
      throw ItemNotFoundException("No visit request found for reference $visitReference")
    }

    val approvedVisitDto = visitDtoBuilder.build(visitRepository.findByReference(visitReference)!!)

    val approvedEventAuditDto = visitEventAuditService.saveVisitRequestApprovedEventAudit(approveVisitRequestBodyDto.actionedBy, approvedVisitDto)

    visitNotificationEventService.deleteVisitNotificationEvents(approvedVisitDto.reference, UnFlagEventReason.VISIT_REQUEST_APPROVED)

    return VisitRequestApprovalResponseDto(approvedVisitDto, approvedEventAuditDto)
  }
}
