package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.PrisonUserClientDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdatePrisonDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonUserClient
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonUserClientRepository
import java.time.LocalDate
import java.util.stream.Collectors

@Service
@Transactional
class PrisonConfigService(
  private val prisonRepository: PrisonRepository,
  private val prisonExcludeDateRepository: PrisonExcludeDateRepository,
  private val messageService: MessageService,
  private val prisonsService: PrisonsService,
  private val visitNotificationEventService: VisitNotificationEventService,
  private val telemetryClientService: TelemetryClientService,
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
  fun activatePrisonClient(prisonCode: String, type: UserType): PrisonUserClientDto {
    return createOrUpdatePrisonClient(prisonCode, type, true)
  }

  @Transactional
  fun deActivatePrisonClient(prisonCode: String, type: UserType): PrisonUserClientDto {
    return createOrUpdatePrisonClient(prisonCode, type, false)
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

  @Transactional
  fun deActivatePrison(prisonCode: String): PrisonDto {
    val prisonToUpdate = prisonsService.findPrisonByCode(prisonCode)
    prisonToUpdate.active = false
    return prisonsService.mapEntityToDto(prisonToUpdate)
  }

  // TODO - replace PrisonDto with all exclude dates for a prison or void
  @Throws(ValidationException::class)
  @Transactional
  fun addExcludeDate(prisonCode: String, prisonExcludeDateDto: PrisonExcludeDateDto): PrisonDto {
    val excludeDate = prisonExcludeDateDto.excludeDate
    // TODO - remove ?: "NOT_KNOWN" when we remove the admin functionality of adding exclude date
    val actionedBy = prisonExcludeDateDto.actionedBy ?: "NOT_KNOWN"

    LOG.info("adding exclude date - {} for prison - {} by user - {}", excludeDate, prisonCode, actionedBy)
    if (isExcludeDateInPast(excludeDate)) {
      LOG.info("failed to add exclude date - {} for prison - {} as exclude date is in the past", excludeDate, prisonCode)
      throw ValidationException(messageService.getMessage("validation.add.prison.excludedate.inpast", prisonCode, excludeDate.toString()))
    }
    val prison = prisonsService.findPrisonByCode(prisonCode)
    val existingExcludeDates = getExistingExcludeDates(prison)

    if (existingExcludeDates.contains(excludeDate)) {
      LOG.info("failed to add exclude date - {} for prison - {} as exclude date already exists", excludeDate, prisonCode)
      throw ValidationException(messageService.getMessage("validation.add.prison.excludedate.alreadyexists", prisonCode, excludeDate.toString()))
    } else {
      prisonExcludeDateRepository.saveAndFlush(PrisonExcludeDate(prison.id, prison, excludeDate, actionedBy))
      telemetryClientService.trackAddExcludeDateEvent(prisonCode, prisonExcludeDateDto)
      // add any visits for the date for review
      visitNotificationEventService.handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonCode, excludeDate))
      LOG.info("successfully added exclude date - {} for prison - {}, by user - {}", excludeDate, prisonCode, actionedBy)
    }
    return PrisonDto(prisonsService.findPrisonByCode(prisonCode))
  }

  @Throws(ValidationException::class)
  @Transactional
  fun removeExcludeDate(prisonCode: String, prisonExcludeDateDto: PrisonExcludeDateDto) {
    val excludeDate = prisonExcludeDateDto.excludeDate
    // TODO - remove ?: "NOT_KNOWN" when we remove the admin functionality of adding exclude date
    val actionedBy = prisonExcludeDateDto.actionedBy ?: "NOT_KNOWN"

    LOG.info("removing exclude date - {} for prison - {} by user - {}", excludeDate, prisonCode, actionedBy)

    val prison = prisonsService.findPrisonByCode(prisonCode)
    val existingExcludeDates = getExistingExcludeDates(prison)

    if (!existingExcludeDates.contains(excludeDate)) {
      LOG.info("failed to remove exclude date - {} for prison - {} as exclude date does not exist", excludeDate, prisonCode)
      throw ValidationException(messageService.getMessage("validation.remove.prison.excludedate.doesnotexist", prisonCode, excludeDate.toString()))
    } else {
      prisonExcludeDateRepository.deleteByPrisonIdAndExcludeDate(prison.id, excludeDate)
      telemetryClientService.trackRemoveExcludeDateEvent(prisonCode, prisonExcludeDateDto)

      visitNotificationEventService.handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(prisonCode, excludeDate))
      LOG.info("successfully removed exclude date - {} for prison - {}, by user - {}", excludeDate, prisonCode, actionedBy)
    }
  }

  private fun getExistingExcludeDates(prison: Prison): Set<LocalDate> {
    return prison.excludeDates.stream().map { it.excludeDate }.collect(Collectors.toSet())
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

  @Transactional(readOnly = true)
  fun getPrisonExcludeDates(prisonCode: String): List<PrisonExcludeDateDto> {
    LOG.debug("getting exclude dates for prison - {}", prisonCode)
    // ensure the prison is enabled
    prisonsService.findPrisonByCode(prisonCode)

    return prisonExcludeDateRepository.getExcludeDatesByPrisonCode(prisonCode).map {
      PrisonExcludeDateDto(it.excludeDate, it.actionedBy)
    }
  }

  private fun isExcludeDateInPast(excludeDate: LocalDate): Boolean {
    return (excludeDate < LocalDate.now())
  }
}
