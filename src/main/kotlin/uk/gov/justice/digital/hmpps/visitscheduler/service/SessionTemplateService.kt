package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.util.function.Supplier

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSessionTemplates(): List<SessionTemplateDto> {
    return sessionTemplateRepository.findAll().sortedBy { it.validFromDate }.map { SessionTemplateDto(it) }
  }

  fun getSessionTemplates(sessionTemplateId: Long): SessionTemplateDto {
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }

  fun getSessionTemplateByReference(sessionTemplateReference: String): SessionTemplateDto {
    return sessionTemplateRepository.findById(1).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $1 not found"))
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}
