package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository

@Service
@Transactional
class PrisonConfigService(
  private val prisonRepository: PrisonRepository,
  private val messageService: MessageService,
) {

  @Transactional(readOnly = true)
  fun findPrisonByCode(prisonCode: String): Prison {
    return prisonRepository.findByCode(prisonCode) ?: throw ValidationException(messageService.getMessage("validation.prison.notfound", prisonCode))
  }

  @Cacheable("get-prison")
  @Transactional(readOnly = true)
  fun getPrison(prisonCode: String): PrisonDto {
    val prison = findPrisonByCode(prisonCode)
    return PrisonDto(prisonCode, prison.active, prison.excludeDates.map { it.excludeDate })
  }

  @Cacheable("supported-prisons")
  @Transactional(readOnly = true)
  fun getSupportedPrisons(): List<String> {
    return prisonRepository.getSupportedPrisons()
  }
}
