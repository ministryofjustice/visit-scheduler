package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import javax.validation.ValidationException

@Service
@Transactional
class PrisonConfigService(
  private val prisonRepository: PrisonRepository,
  private val messageService: MessageService
) {

  @Transactional(readOnly = true)
  fun findPrisonByCode(prisonCode: String): Prison {
    return prisonRepository.findByCode(prisonCode) ?: throw ValidationException(messageService.getMessage("validation.prison.notfound", prisonCode))
  }

  @Transactional(readOnly = true)
  fun getSupportedPrisons(): List<String> {
    return prisonRepository.getSupportedPrisons()
  }
}
