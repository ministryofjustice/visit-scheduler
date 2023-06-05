package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDatesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateAction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateExcludeDatesDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import java.time.LocalDate
import java.util.function.Predicate
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

  @Cacheable("get-prison")
  @Transactional(readOnly = true)
  fun getPrison(prisonCode: String): PrisonDto {
    val prison = findPrisonByCode(prisonCode)
    return mapEntityToDto(prison)
  }

  @Cacheable("get-prisons")
  @Transactional(readOnly = true)
  fun getPrisons(): List<PrisonDto> {
    val prisons = prisonRepository.findAllByOrderByCodeAsc()

    return prisons.map { mapEntityToDto(it) }
  }

  private fun mapEntityToDto(it: Prison): PrisonDto {
    return PrisonDto(it.code, it.active, it.excludeDates.map { it.excludeDate }.toSortedSet())
  }

  @Transactional(readOnly = true)
  fun isExcludedDate(prisonCode: String, date: LocalDate): Boolean {
    val prison = findPrisonByCode(prisonCode)
    return prison.excludeDates.find { it.excludeDate.compareTo(date) == 0 } != null
  }

  @Cacheable("supported-prisons")
  @Transactional(readOnly = true)
  fun getSupportedPrisons(): List<String> {
    return prisonRepository.getSupportedPrisons()
  }

  @Transactional
  fun createPrison(prisonDto: PrisonDto): PrisonDto {
    if (prisonRepository.findByCode(prisonDto.code) != null) {
      throw ValidationException(messageService.getMessage("validation.create.prison.found", prisonDto.code))
    }

    val newPrison = Prison(prisonDto.code, prisonDto.active)
    val savedPrison = prisonRepository.saveAndFlush(newPrison)
    val excludeDates = prisonDto.excludeDates.map { PrisonExcludeDate(prisonId = savedPrison.id, prison = savedPrison, it) }
    savedPrison.excludeDates.addAll(excludeDates)

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

  @Transactional
  fun updateExcludeDates(prisonCode: String, updateExcludeDatesDto: UpdateExcludeDatesDto) {
    val prison = findPrisonByCode(prisonCode)
    val existingExcludeDates = getExistingExcludeDates(prison)
    val excludeDatesList = updateExcludeDatesDto.excludeDates.stream().toList()
    val addExcludeDates = getPrisonExcludeDatesByAction(prison, existingExcludeDates, excludeDatesList, UpdateAction.ADD)
    val removeExcludeDates = getPrisonExcludeDatesByAction(prison, existingExcludeDates, excludeDatesList, UpdateAction.REMOVE)

    if (addExcludeDates.isNotEmpty()) {
      prisonExcludeDateRepository.saveAll(addExcludeDates)
    }

    if (removeExcludeDates.isNotEmpty()) {
      removeExcludeDates.forEach {
        prisonExcludeDateRepository.deleteByPrisonIdAndExcludeDate(prison.id, it.excludeDate)
      }
    }
  }

  private fun getExistingExcludeDates(prison: Prison): Set<LocalDate> {
    return prison.excludeDates.stream().map { it.excludeDate }.collect(Collectors.toSet())
  }

  private fun getPrisonExcludeDatesByAction(
    prison: Prison,
    existingExcludeDates: Set<LocalDate>,
    excludeDatesList: List<ExcludeDatesDto>,
    updateAction: UpdateAction,
  ): List<PrisonExcludeDate> {
    val addFilter = Predicate { excludeDatesDto: ExcludeDatesDto -> excludeDatesDto.action == UpdateAction.ADD }
    val removeFilter = Predicate { excludeDatesDto: ExcludeDatesDto -> excludeDatesDto.action == UpdateAction.REMOVE }

    return when (updateAction) {
      UpdateAction.ADD -> {
        excludeDatesList.stream().filter { addFilter.test(it) }
          .filter { !existingExcludeDates.contains(it.excludeDate) }
          .map { PrisonExcludeDate(prison.id, prison, it.excludeDate) }.toList()
      }
      UpdateAction.REMOVE -> {
        excludeDatesList.stream().filter { removeFilter.test(it) }
          .filter { existingExcludeDates.contains(it.excludeDate) }
          .map { PrisonExcludeDate(prison.id, prison, it.excludeDate) }.toList()
      }
    }
  }
}
