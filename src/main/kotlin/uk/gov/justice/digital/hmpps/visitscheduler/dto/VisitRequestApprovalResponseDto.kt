package uk.gov.justice.digital.hmpps.visitscheduler.dto

import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.EventAuditDto

data class VisitRequestApprovalResponseDto(
  val visitDto: VisitDto,
  val eventAuditDto: EventAuditDto,
)
