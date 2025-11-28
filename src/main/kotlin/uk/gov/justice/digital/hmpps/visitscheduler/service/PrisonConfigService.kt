package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.Validation
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
import java.time.LocalDateTime

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
    const val DEFAULT_BOOKING_MIN_DAYS = 2
    const val DEFAULT_BOOKING_MAX_DAYS = 28
  }

  @Autowired
  private lateinit var prisonUserClientRepository: PrisonUserClientRepository

  @Transactional
  fun createPrison(prisonDto: PrisonDto): PrisonDto {
    if (prisonRepository.findByCode(prisonDto.code) != null) {
      throw ValidationException(messageService.getMessage("validation.create.prison.found", prisonDto.code))
    }
    validatePrisonDetails(
      prisonDto.code,
      prisonDto.maxTotalVisitors,
      prisonDto.maxAdultVisitors,
      prisonDto.maxChildVisitors,
      prisonDto.clients,
    )

    val newPrison = Prison(prisonDto)
    val savedPrison = prisonRepository.saveAndFlush(newPrison)

    val clients = prisonDto.clients.map { PrisonUserClient(prisonId = savedPrison.id, prison = savedPrison, userType = it.userType, policyNoticeDaysMin = it.policyNoticeDaysMin, policyNoticeDaysMax = it.policyNoticeDaysMax, active = it.active) }
    savedPrison.clients.addAll(clients)

    return prisonsService.mapEntityToDto(savedPrison)
  }

  fun updatePrison(prisonCode: String, prisonDto: UpdatePrisonDto): PrisonDto {
    var prison = prisonsService.findPrisonByCode(prisonCode)

    val maxTotalVisitors = prisonDto.maxTotalVisitors ?: prison.maxTotalVisitors
    val maxAdultVisitors = prisonDto.maxAdultVisitors ?: prison.maxAdultVisitors
    val maxChildVisitors = prisonDto.maxChildVisitors ?: prison.maxChildVisitors
    val adultAgeYears = prisonDto.adultAgeYears ?: prison.adultAgeYears

    validatePrisonDetails(prisonCode, maxTotalVisitors, maxAdultVisitors, maxChildVisitors, prisonDto.clients)
    prison.maxTotalVisitors = maxTotalVisitors
    prison.maxAdultVisitors = maxAdultVisitors
    prison.maxChildVisitors = maxChildVisitors
    prison.adultAgeYears = adultAgeYears

    prison = prisonRepository.saveAndFlush(prison)
    if (prisonDto.clients != null) {
      prison.clients.clear()
      prisonDto.clients.forEach {
        prison.clients.add(
          PrisonUserClient(
            prisonId = prison.id,
            prison = prison,
            userType = it.userType,
            policyNoticeDaysMin = it.policyNoticeDaysMin,
            policyNoticeDaysMax = it.policyNoticeDaysMax,
            active = it.active,
            createTimestamp = LocalDateTime.now(),
            modifyTimestamp = LocalDateTime.now(),
          ),
        )
      }
    }

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
      prisonUserClient = PrisonUserClient(prison.id, prison, userType, policyNoticeDaysMin = DEFAULT_BOOKING_MIN_DAYS, policyNoticeDaysMax = DEFAULT_BOOKING_MAX_DAYS, active)
      prison.clients.add(prisonUserClient)
    }
    return PrisonUserClientDto(prisonUserClient)
  }

  private fun validatePrisonDetails(
    prisonCode: String,
    maxTotalVisitors: Int,
    maxAdultVisitors: Int,
    maxChildVisitors: Int,
    clients: List<PrisonUserClientDto>?,
  ) {
    validateTotalVisitors(prisonCode, maxTotalVisitors = maxTotalVisitors, maxAdultVisitors = maxAdultVisitors, maxChildVisitors = maxChildVisitors)

    if (!clients.isNullOrEmpty()) {
      validatePrisonClients(prisonCode = prisonCode, clients = clients)
    }
  }

  private fun validateTotalVisitors(
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
  }

  private fun validatePrisonClients(
    clients: List<PrisonUserClientDto>,
    prisonCode: String,
  ) {
    val validator = Validation.buildDefaultValidatorFactory().validator

    clients.forEach { client ->
      val validationErrors = validator.validate(client)
      if (validationErrors.isNotEmpty()) {
        throw ValidationException(validationErrors.joinToString(separator = ", ") { it.message })
      }
      if (client.policyNoticeDaysMin > client.policyNoticeDaysMax) {
        throw ValidationException(
          messageService.getMessage(
            "validation.prison.policynoticedays.invalid",
            prisonCode,
            client.policyNoticeDaysMin.toString(),
            client.policyNoticeDaysMax.toString(),
            client.userType.name,
          ),
        )
      }
    }
  }
}
