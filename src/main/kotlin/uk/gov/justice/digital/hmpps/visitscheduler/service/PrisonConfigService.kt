package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate
import java.util.stream.Collectors

@Service
@Transactional
class PrisonConfigService(
  private val prisonRepository: PrisonRepository,
  private val prisonExcludeDateRepository: PrisonExcludeDateRepository,
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

  private fun mapEntityToDto(it: Prison): PrisonDto {
    return PrisonDto(it.code, it.active, it.policyNoticeDaysMin, it.policyNoticeDaysMax, it.updatePolicyNoticeDaysMin, it.excludeDates.map { it.excludeDate }.toSortedSet())
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

  @Transactional
  fun createPrison(prisonDto: PrisonDto): PrisonDto {
    if (prisonRepository.findByCode(prisonDto.code) != null) {
      throw ValidationException(messageService.getMessage("validation.create.prison.found", prisonDto.code))
    }
    validatePrisonDetails(prisonDto.updatePolicyNoticeDaysMin, prisonDto.policyNoticeDaysMin, prisonDto.policyNoticeDaysMax, prisonDto.code)

    val newPrison = Prison(prisonDto.code, prisonDto.active, prisonDto.policyNoticeDaysMin, prisonDto.policyNoticeDaysMax, prisonDto.updatePolicyNoticeDaysMin)
    val savedPrison = prisonRepository.saveAndFlush(newPrison)
    val excludeDates = prisonDto.excludeDates.map { PrisonExcludeDate(prisonId = savedPrison.id, prison = savedPrison, it) }
    savedPrison.excludeDates.addAll(excludeDates)

    return mapEntityToDto(savedPrison)
  }

  fun updatePrison(prisonCode: String, prisonDto: UpdatePrisonDto): PrisonDto {
    val prison = findPrisonByCode(prisonCode)

    val policyNoticeDaysMin = prisonDto.policyNoticeDaysMin ?: prison.policyNoticeDaysMin
    val updatePolicyNoticeDaysMin = prisonDto.updatePolicyNoticeDaysMin ?: prison.updatePolicyNoticeDaysMin
    val policyNoticeDaysMax = prisonDto.policyNoticeDaysMax ?: prison.policyNoticeDaysMax

    validatePrisonDetails(updatePolicyNoticeDaysMin, policyNoticeDaysMin, policyNoticeDaysMax, prisonCode)

    prison.policyNoticeDaysMin = policyNoticeDaysMin
    prison.policyNoticeDaysMax = policyNoticeDaysMax
    prison.updatePolicyNoticeDaysMin = updatePolicyNoticeDaysMin

    val savedPrison = prisonRepository.saveAndFlush(prison)
    return mapEntityToDto(savedPrison)
  }

  @Transactional
  fun activatePrison(prisonCode: String): PrisonDto {
    val prisonToUpdate = findPrisonByCode(prisonCode)
    prisonToUpdate.active = true
    return mapEntityToDto(prisonToUpdate)
  }

  @Transactional
  fun deActivatePrison(prisonCode: String): PrisonDto {
    val prisonToUpdate = findPrisonByCode(prisonCode)
    prisonToUpdate.active = false
    return mapEntityToDto(prisonToUpdate)
  }

  @Throws(ValidationException::class)
  @Transactional
  fun addExcludeDate(prisonCode: String, excludeDate: LocalDate): PrisonDto {
    val prison = findPrisonByCode(prisonCode)
    val existingExcludeDates = getExistingExcludeDates(prison)

    if (existingExcludeDates.contains(excludeDate)) {
      throw ValidationException(messageService.getMessage("validation.add.prison.excludedate.alreadyexists", prisonCode, excludeDate.toString()))
    } else {
      prisonExcludeDateRepository.saveAndFlush(PrisonExcludeDate(prison.id, prison, excludeDate))
    }
    return PrisonDto(findPrisonByCode(prisonCode))
  }

  @Throws(ValidationException::class)
  @Transactional
  fun removeExcludeDate(prisonCode: String, excludeDate: LocalDate) {
    val prison = findPrisonByCode(prisonCode)
    val existingExcludeDates = getExistingExcludeDates(prison)

    if (!existingExcludeDates.contains(excludeDate)) {
      throw ValidationException(messageService.getMessage("validation.remove.prison.excludedate.doesnotexist", prisonCode, excludeDate.toString()))
    } else {
      prisonExcludeDateRepository.deleteByPrisonIdAndExcludeDate(prison.id, excludeDate)
    }
  }

  private fun getExistingExcludeDates(prison: Prison): Set<LocalDate> {
    return prison.excludeDates.stream().map { it.excludeDate }.collect(Collectors.toSet())
  }

  private fun validatePrisonDetails(
    updatePolicyNoticeDaysMin: Int,
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int,
    prisonCode: String,
  ) {
    if (updatePolicyNoticeDaysMin > policyNoticeDaysMin) {
      throw ValidationException(
        messageService.getMessage(
          "validation.prison.updatepolicynoticedays.invalid",
          prisonCode,
          updatePolicyNoticeDaysMin.toString(),
          policyNoticeDaysMin.toString(),
        ),
      )
    }
    if (policyNoticeDaysMin > policyNoticeDaysMax) {
      throw ValidationException(
        messageService.getMessage(
          "validation.prison.policynoticedays.invalid",
          prisonCode,
          policyNoticeDaysMin.toString(),
          policyNoticeDaysMax.toString(),
        ),
      )
    }
  }
}
