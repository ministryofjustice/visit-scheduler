package uk.gov.justice.digital.hmpps.visitscheduler.dto.builder

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import java.time.LocalDateTime

@Component
class ApplicationDtoBuilder {

  fun build(applicationEntity: Application): ApplicationDto {
    return ApplicationDto(
      reference = applicationEntity.reference,
      prisonerId = applicationEntity.prisonerId,
      prisonCode = applicationEntity.prison.code,
      visitType = applicationEntity.visitType,
      visitRestriction = applicationEntity.restriction,
      startTimestamp = applicationEntity.sessionSlot.slotStart,
      endTimestamp = applicationEntity.sessionSlot.slotEnd,
      visitContact = applicationEntity.visitContact?.let { ContactDto(it) },
      visitors = applicationEntity.visitors.map { VisitorDto(it) },
      visitorSupport = applicationEntity.support.map { VisitorSupportDto(it) },
      createdTimestamp = applicationEntity.createTimestamp ?: LocalDateTime.now(),
      modifiedTimestamp = applicationEntity.modifyTimestamp ?: LocalDateTime.now(),
      sessionTemplateReference = applicationEntity.sessionSlot.sessionTemplateReference,
      reserved = applicationEntity.reservedSlot,
      completed = applicationEntity.completed,
    )
  }
}
