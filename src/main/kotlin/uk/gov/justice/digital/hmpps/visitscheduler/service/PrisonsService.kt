package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate

@Service
@Transactional
class PrisonsService(
  private val prisonRepository: PrisonRepository,
  private val messageService: MessageService,
) {

  @Transactional(readOnly = true)
  fun findPrisonByCode(prisonCode: String): Prison {
    return prisonRepository.findByCode(prisonCode) ?: throw ValidationException(messageService.getMessage("validation.prison.notfound", prisonCode))
  }

  @Transactional(readOnly = true)
  fun getPrison(prisonCode: String): PrisonDto {
    val prison = findPrisonByCode(prisonCode)
    return mapEntityToDto(prison)
  }

  @Transactional(readOnly = true)
  fun getPrisons(): List<PrisonDto> {
    val prisons = prisonRepository.findAllByOrderByCodeAsc()

    return prisons.map { mapEntityToDto(it) }
  }

  fun mapEntityToDto(it: Prison): PrisonDto {
    return PrisonDto(it.code, it.active, it.policyNoticeDaysMin, it.policyNoticeDaysMax, it.excludeDates.map { it.excludeDate }.toSortedSet())
  }

  @Transactional(readOnly = true)
  fun isExcludedDate(prisonCode: String, date: LocalDate): Boolean {
    val prison = findPrisonByCode(prisonCode)
    return prison.excludeDates.find { it.excludeDate.compareTo(date) == 0 } != null
  }

  @Transactional(readOnly = true)
  fun getSupportedPrisons(): List<String> {
    return prisonRepository.getSupportedPrisons()
  }

  @Transactional(readOnly = true)
  fun getSupportedPrison(prisonCode: String): String? {
    return prisonRepository.getSupportedPrison(prisonCode)
  }
}
