package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveRejectionVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestApprovalRejectionResponseDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.VisitDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@Transactional
@Service
class VisitRequestsApprovalRejectionService(
  private val visitRepository: VisitRepository,
  private val visitEventAuditService: VisitEventAuditService,
  private val visitDtoBuilder: VisitDtoBuilder,
  private val visitNotificationEventService: VisitNotificationEventService,
  private val messageService: MessageService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun manuallyApproveOrRejectVisitRequestByReference(approvalVisitRequestBodyDto: ApproveRejectionVisitRequestBodyDto, isApproved: Boolean): VisitRequestApprovalRejectionResponseDto {
    val visitReference = approvalVisitRequestBodyDto.visitReference

    LOG.info("approveOrRejectVisitRequestByReference - called for visit - ${approvalVisitRequestBodyDto.visitReference}, isApproved - $isApproved")

    val success = if (isApproved) {
      visitRepository.approveVisitRequestForPrisonByReference(visitReference) > 0
    } else {
      visitRepository.rejectVisitRequestForPrisonByReference(visitReference) > 0
    }
    if (!success) {
      throw ValidationException(messageService.getMessage("validation.visitrequests.invalidstatus", visitReference))
    }

    val actionedVisitDto = visitDtoBuilder.build(visitRepository.findByReference(visitReference)!!)

    val eventAuditDto = visitEventAuditService.saveVisitRequestApprovedOrRejectedEventAudit(approvalVisitRequestBodyDto.actionedBy, actionedVisitDto, isApproved)

    val unflagEventReason = if (isApproved) {
      UnFlagEventReason.VISIT_REQUEST_APPROVED
    } else {
      UnFlagEventReason.VISIT_REQUEST_REJECTED
    }
    visitNotificationEventService.deleteVisitAndPairedNotificationEvents(actionedVisitDto.reference, unflagEventReason)

    return VisitRequestApprovalRejectionResponseDto(actionedVisitDto, eventAuditDto)
  }

  fun autoRejectRequestByVisitReference(visitReference: String, rejectionText: String): VisitRequestApprovalRejectionResponseDto {
    LOG.info("Entered VisitRequestsApprovalRejectionService - autoRejectRequestVisitsAtMinimumBookingWindow")

    visitRepository.autoRejectVisitRequestByReference(visitReference)

    val actionedVisitDto = visitDtoBuilder.build(visitRepository.findByReference(visitReference)!!)
    val eventAuditDto = visitEventAuditService.saveVisitRequestAutoRejectedEventAudit(actionedVisitDto, rejectionText)

    visitNotificationEventService.deleteVisitAndPairedNotificationEvents(actionedVisitDto.reference, UnFlagEventReason.VISIT_REQUEST_AUTO_REJECTED)

    return VisitRequestApprovalRejectionResponseDto(visitDto = actionedVisitDto, eventAuditDto = eventAuditDto)
  }
}
