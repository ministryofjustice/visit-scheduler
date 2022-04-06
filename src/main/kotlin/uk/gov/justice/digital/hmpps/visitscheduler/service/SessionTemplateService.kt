package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSessionTemplateRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.util.function.Supplier

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository,
) {

  fun createSessionTemplate(createSessionTemplateRequest: CreateSessionTemplateRequestDto): SessionTemplateDto {
    log.info("Creating session template for prison")
    val sessionTemplateEntity = sessionTemplateRepository.saveAndFlush(
      SessionTemplate(
        prisonId = createSessionTemplateRequest.prisonId,
        startTime = createSessionTemplateRequest.startTime,
        endTime = createSessionTemplateRequest.endTime,
        visitType = createSessionTemplateRequest.visitType,
        startDate = createSessionTemplateRequest.startDate,
        expiryDate = createSessionTemplateRequest.expiryDate,
        frequency = createSessionTemplateRequest.frequency,
        visitRoom = createSessionTemplateRequest.visitRoom,
        closedCapacity = createSessionTemplateRequest.closedCapacity,
        openCapacity = createSessionTemplateRequest.openCapacity,
        restrictions = createSessionTemplateRequest.restrictions
      )
    )
    return SessionTemplateDto(
      sessionTemplateEntity
    )
  }

  fun getSessionTemplates(): List<SessionTemplateDto> {
    return sessionTemplateRepository.findAll().sortedBy { it.startDate }.map { SessionTemplateDto(it) }
  }

  fun getSessionTemplates(sessionTemplateId: Long): SessionTemplateDto {
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }

  fun deleteSessionTemplate(sessionTemplateId: Long) {
    val sessionTemplate = sessionTemplateRepository.findByIdOrNull(sessionTemplateId)
    sessionTemplate?.let { sessionTemplateRepository.delete(it) }.also { log.info("Session template with id  $sessionTemplateId deleted") }
      ?: run {
        log.info("Session template with id  $sessionTemplateId not found")
      }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}
