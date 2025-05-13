package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonUserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonUserClientRepository

@Service
@Transactional
class PrisonConfigService(
  private val prisonRepository: PrisonRepository,
  private val messageService: MessageService,
  private val prisonsService: PrisonsService,
  private val excludeDateService: ExcludeDateService,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  private lateinit var prisonUserClientRepository: PrisonUserClientRepository

  @Transactional
  fun createPrison(prisonDto: PrisonDto): PrisonDto {
    if (prisonRepository.findByCode(prisonDto.code) != null) {
      throw ValidationException(messageService.getMessage("validation.create.prison.found", prisonDto.code))
    }
    validatePrisonDetails(
      prisonDto.policyNoticeDaysMin,
      prisonDto.policyNoticeDaysMax,
      prisonDto.code,
      prisonDto.maxTotalVisitors,
      prisonDto.maxAdultVisitors,
      prisonDto.maxChildVisitors,
    )

    val newPrison = Prison(prisonDto)
    val savedPrison = prisonRepository.saveAndFlush(newPrison)

    val clients = prisonDto.clients.map { PrisonUserClient(prisonId = savedPrison.id, prison = savedPrison, userType = it.userType, active = it.active) }
    savedPrison.clients.addAll(clients)

    return prisonsService.mapEntityToDto(savedPrison)
  }

  fun updatePrison(prisonCode: String, prisonDto: UpdatePrisonDto): PrisonDto {
    val prison = prisonsService.findPrisonByCode(prisonCode)

    val policyNoticeDaysMin = prisonDto.policyNoticeDaysMin ?: prison.policyNoticeDaysMin
    val policyNoticeDaysMax = prisonDto.policyNoticeDaysMax ?: prison.policyNoticeDaysMax

    val maxTotalVisitors = prisonDto.maxTotalVisitors ?: prison.maxTotalVisitors
    val maxAdultVisitors = prisonDto.maxAdultVisitors ?: prison.maxAdultVisitors
    val maxChildVisitors = prisonDto.maxChildVisitors ?: prison.maxChildVisitors
    val adultAgeYears = prisonDto.adultAgeYears ?: prison.adultAgeYears

    validatePrisonDetails(policyNoticeDaysMin, policyNoticeDaysMax, prisonCode, maxTotalVisitors, maxAdultVisitors, maxChildVisitors)

    prison.policyNoticeDaysMin = policyNoticeDaysMin
    prison.policyNoticeDaysMax = policyNoticeDaysMax
    prison.maxTotalVisitors = maxTotalVisitors
    prison.maxAdultVisitors = maxAdultVisitors
    prison.maxChildVisitors = maxChildVisitors
    prison.adultAgeYears = adultAgeYears

    val savedPrison = prisonRepository.saveAndFlush(prison)
    return prisonsService.mapEntityToDto(savedPrison)
  }

  @Transactional
  fun activatePrison(prisonCode: String): PrisonDto {
    val prisonToUpdate = prisonsService.findPrisonByCode(prisonCode)
    prisonToUpdate.active = true
    return prisonsService.mapEntityToDto(prisonToUpdate)
  }

  @Transactional
  fun activatePrisonClient(prisonCode: String, type: UserType): PrisonUserClientDto = createOrUpdatePrisonClient(prisonCode, type, true)

  @Transactional
  fun deActivatePrisonClient(prisonCode: String, type: UserType): PrisonUserClientDto = createOrUpdatePrisonClient(prisonCode, type, false)

  @Transactional
  fun deActivatePrison(prisonCode: String): PrisonDto {
    val prisonToUpdate = prisonsService.findPrisonByCode(prisonCode)
    prisonToUpdate.active = false
    return prisonsService.mapEntityToDto(prisonToUpdate)
  }

  @Throws(ValidationException::class)
  @Transactional
  fun addExcludeDate(prisonCode: String, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      LOG.info("adding exclude date - {} for prison - {} by user - {}", excludeDate, prisonCode, actionedBy)
      val prison = prisonsService.findPrisonByCode(prisonCode)
      excludeDateService.addExcludeDate(prison, excludeDateDto)
      LOG.info("successfully  added exclude date - {} for prison - {}, by user - {}", excludeDate, prisonCode, actionedBy)
    }
  }

  @Throws(ValidationException::class)
  @Transactional
  fun removeExcludeDate(prisonCode: String, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      LOG.info("removing exclude date - {} for prison - {} by user - {}", excludeDate, prisonCode, actionedBy)
      val prison = prisonsService.findPrisonByCode(prisonCode)
      excludeDateService.removeExcludeDate(prison, excludeDateDto)
      LOG.info("successfully  removed exclude date - {} for prison - {}, by user - {}", excludeDate, prisonCode, actionedBy)
    }
  }

  @Transactional(readOnly = true)
  fun getExcludeDates(prisonCode: String): List<ExcludeDateDto> {
    LOG.debug("getting exclude dates for prison - {}", prisonCode)
    // ensure the prison is enabled
    val prison = prisonsService.findPrisonByCode(prisonCode)
    return excludeDateService.getExcludeDates(prison.excludeDates)
  }

  private fun createOrUpdatePrisonClient(
    prisonCode: String,
    userType: UserType,
    active: Boolean,
  ): PrisonUserClientDto {
    val prisonUserClient: PrisonUserClient
    if (prisonUserClientRepository.doesPrisonClientExist(prisonCode, userType)) {
      prisonUserClient = prisonUserClientRepository.getPrisonClient(prisonCode, userType)
      prisonUserClient.active = active
    } else {
      val prison = prisonsService.findPrisonByCode(prisonCode)
      prisonUserClient = PrisonUserClient(prison.id, prison, userType, active)
      prison.clients.add(prisonUserClient)
    }
    return PrisonUserClientDto(prisonUserClient.userType, prisonUserClient.active)
  }

  private fun validatePrisonDetails(
    policyNoticeDaysMin: Int,
    policyNoticeDaysMax: Int,
    prisonCode: String,
    maxTotalVisitors: Int,
    maxAdultVisitors: Int,
    maxChildVisitors: Int,
  ) {
    val highestMax = if (maxAdultVisitors > maxChildVisitors) maxAdultVisitors else maxChildVisitors
    if (maxTotalVisitors < highestMax) {
      throw ValidationException(
        messageService.getMessage(
          "validation.prison.maxTotalVisitors.invalid",
          prisonCode,
          maxTotalVisitors.toString(),
          highestMax.toString(),
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
