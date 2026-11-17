package uk.gov.justice.digital.hmpps.visitscheduler.dto.audit

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditAttributeTypes
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAuditAttribute

data class EventAuditAttributeDto(
  @param:Schema(description = "Name of the attribute associated with the event audit", example = "AUTO_REJECTION_REASON", required = true)
  @field:NotNull
  val attributeName: EventAuditAttributeTypes,

  @param:Schema(description = "Value of the attribute associated with the event audit", example = "Transfer", required = true)
  @field:NotNull
  val attributeValue: String,
) {
  constructor(eventAuditAttribute: EventAuditAttribute) : this (
    attributeName = eventAuditAttribute.attributeName,
    attributeValue = eventAuditAttribute.attributeValue,
  )
}
