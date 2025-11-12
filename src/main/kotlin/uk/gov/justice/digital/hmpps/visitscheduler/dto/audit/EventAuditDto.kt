package uk.gov.justice.digital.hmpps.visitscheduler.dto.audit

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.NotifyHistoryDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.validation.NullableNotBlank
import java.time.LocalDateTime

@Schema(description = "Event Audit")
class EventAuditDto(

  @param:Schema(description = "The id of the event", required = true)
  val id: Long,

  @param:Schema(description = "The type of event", required = true)
  @field:NotNull
  val type: EventAuditType,

  @param:Schema(description = "What was the application method for this event", required = true)
  @field:NotNull
  val applicationMethodType: ApplicationMethodType,

  @param:Schema(description = "Event actioned by information", required = true)
  @field:NotNull
  @field:Valid
  val actionedBy: ActionedByDto,

  @param:Schema(description = "Visit reference", required = false)
  val bookingReference: String? = null,

  @param:Schema(description = "Session template used for this event", required = false)
  var sessionTemplateReference: String? = null,

  @param:Schema(description = "Notes added against the event", required = false)
  @NullableNotBlank
  var text: String? = null,

  @param:Schema(description = "Notify history for the event", required = false)
  @NullableNotBlank
  val notifyHistory: List<NotifyHistoryDto> = emptyList(),

  @param:Schema(description = "event creat date and time", example = "2018-12-01T13:45:00", required = true)
  @field:NotBlank
  val createTimestamp: LocalDateTime = LocalDateTime.now(),
) {
  constructor(eventAuditEntity: EventAudit, notifyHistoryDtoBuilder: NotifyHistoryDtoBuilder) : this(
    id = eventAuditEntity.id,
    type = eventAuditEntity.type,
    applicationMethodType = eventAuditEntity.applicationMethodType,
    actionedBy = ActionedByDto(eventAuditEntity.actionedBy),
    bookingReference = eventAuditEntity.bookingReference,
    sessionTemplateReference = eventAuditEntity.sessionTemplateReference,
    createTimestamp = eventAuditEntity.createTimestamp,
    text = eventAuditEntity.text,
    notifyHistory = notifyHistoryDtoBuilder.build(eventAuditEntity.notifyHistory),
  )

  constructor(eventAuditEntity: EventAudit) : this(
    id = eventAuditEntity.id,
    type = eventAuditEntity.type,
    applicationMethodType = eventAuditEntity.applicationMethodType,
    actionedBy = ActionedByDto(eventAuditEntity.actionedBy),
    bookingReference = eventAuditEntity.bookingReference,
    sessionTemplateReference = eventAuditEntity.sessionTemplateReference,
    createTimestamp = eventAuditEntity.createTimestamp,
    text = eventAuditEntity.text,
  )
}
