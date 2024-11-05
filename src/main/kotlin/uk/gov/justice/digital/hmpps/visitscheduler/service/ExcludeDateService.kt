package uk.gov.justice.digital.hmpps.visitscheduler.service

import jakarta.validation.ValidationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.SessionDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.IExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.PrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.PrisonExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateExcludeDateRepository
import java.time.LocalDate
import java.util.stream.Collectors

@Service
@Transactional
class ExcludeDateService(
  private val messageService: MessageService,
  private val visitNotificationEventService: VisitNotificationEventService,
  private val telemetryClientService: TelemetryClientService,
  private val prisonExcludeDateRepository: PrisonExcludeDateRepository,
  private val sessionTemplateExcludeDateRepository: SessionTemplateExcludeDateRepository,
) {
  companion object {
    val LOG: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Throws(ValidationException::class)
  fun addExcludeDate(prison: Prison, excludeDateDto: ExcludeDateDto) {
    addPrisonExcludeDate(prison, excludeDateDto)
  }

  @Throws(ValidationException::class)
  fun addExcludeDate(sessionTemplate: SessionTemplate, excludeDateDto: ExcludeDateDto) {
    addSessionTemplateExcludeDate(sessionTemplate, excludeDateDto)
  }

  @Throws(ValidationException::class)
  fun removeExcludeDate(prison: Prison, excludeDateDto: ExcludeDateDto) {
    removePrisonExcludeDate(prison, excludeDateDto)
  }

  @Throws(ValidationException::class)
  fun removeExcludeDate(sessionTemplate: SessionTemplate, excludeDateDto: ExcludeDateDto) {
    removeSessionTemplateExcludeDate(sessionTemplate, excludeDateDto)
  }

  fun getExcludeDates(excludeDates: List<IExcludeDate>): List<ExcludeDateDto> {
    return excludeDates.map {
      ExcludeDateDto(it.excludeDate, it.actionedBy)
    }.sortedByDescending { it.excludeDate }
  }

  fun getPrisonExcludeDates(prisonCode: String): List<PrisonExcludeDate> {
    return prisonExcludeDateRepository.getExcludeDatesByPrisonCode(prisonCode)
  }

  private fun addPrisonExcludeDate(prison: Prison, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      val prisonCode = prison.code
      val existingExcludeDates = getExistingExcludeDates(prison)
      validateAddExcludeDate(ExcludeDateEntity.PRISON, excludeDate, prisonCode, existingExcludeDates)

      prisonExcludeDateRepository.saveAndFlush(PrisonExcludeDate(prison.id, prison, excludeDate, actionedBy))
      telemetryClientService.trackAddPrisonExcludeDateEvent(prisonCode, excludeDateDto)

      // add any visits for the date for review
      visitNotificationEventService.handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonCode, excludeDate))
    }
  }

  private fun addSessionTemplateExcludeDate(sessionTemplate: SessionTemplate, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      val sessionTemplateReference = sessionTemplate.reference
      val existingExcludeDates = getExistingExcludeDates(sessionTemplate)
      validateAddExcludeDate(ExcludeDateEntity.SESSION_TEMPLATE, excludeDate, sessionTemplateReference, existingExcludeDates)

      sessionTemplateExcludeDateRepository.saveAndFlush(SessionTemplateExcludeDate(sessionTemplate.id, sessionTemplate, excludeDate, actionedBy))
      telemetryClientService.trackAddSessionExcludeDateEvent(sessionTemplateReference, excludeDateDto)

      // add any visits for the session template for the date for review
      visitNotificationEventService.handleAddSessionVisitBlockDate(SessionDateBlockedDto(sessionTemplateReference, excludeDate))
    }
  }

  private fun removePrisonExcludeDate(prison: Prison, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      val prisonCode = prison.code
      val existingExcludeDates = getExistingExcludeDates(prison)
      validateRemoveExcludeDate(ExcludeDateEntity.PRISON, excludeDate, prisonCode, existingExcludeDates)
      prisonExcludeDateRepository.deleteByPrisonIdAndExcludeDate(prison.id, excludeDate)
      telemetryClientService.trackRemovePrisonExcludeDateEvent(prisonCode, excludeDateDto)

      visitNotificationEventService.handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(prisonCode, excludeDate))
    }
  }

  private fun removeSessionTemplateExcludeDate(sessionTemplate: SessionTemplate, excludeDateDto: ExcludeDateDto) {
    with(excludeDateDto) {
      val sessionTemplateReference = sessionTemplate.reference
      val existingExcludeDates = getExistingExcludeDates(sessionTemplate)
      validateRemoveExcludeDate(ExcludeDateEntity.SESSION_TEMPLATE, excludeDate, sessionTemplateReference, existingExcludeDates)
      sessionTemplateExcludeDateRepository.deleteBySessionTemplateIdAndExcludeDate(sessionTemplate.id, excludeDate)
      telemetryClientService.trackRemoveSessionExcludeDateEvent(sessionTemplateReference, excludeDateDto)

      visitNotificationEventService.handleRemoveSessionVisitBlockDate(SessionDateBlockedDto(sessionTemplateReference, excludeDate))
    }
  }

  private fun validateAddExcludeDate(excludeDateEntity: ExcludeDateEntity, excludeDate: LocalDate, prisonCode: String, existingExcludeDates: Set<LocalDate>) {
    // validate if excluded date is in the past
    validatePastExcludeDate(excludeDateEntity, excludeDate, prisonCode)

    // validate if the exclude date has already been added
    validateAlreadyAddedExcludeDate(excludeDateEntity, existingExcludeDates, excludeDate, prisonCode)
  }

  @kotlin.jvm.Throws(ValidationException::class)
  private fun validatePastExcludeDate(excludeDateEntity: ExcludeDateEntity, excludeDate: LocalDate, code: String) {
    if (isExcludeDateInPast(excludeDate)) {
      LOG.info("failed to add exclude date - {} for {} - {} as exclude date is in the past", excludeDate, excludeDateEntity.desc, code)
      val message = getPastExcludeDateMessage(excludeDateEntity, code, excludeDate) ?: "exclude date is in the past"
      throw ValidationException(message)
    }
  }

  @kotlin.jvm.Throws(ValidationException::class)
  private fun validateAlreadyAddedExcludeDate(excludeDateEntity: ExcludeDateEntity, existingExcludeDates: Set<LocalDate>, excludeDate: LocalDate, code: String) {
    if (existingExcludeDates.contains(excludeDate)) {
      LOG.info("failed to add exclude date - {} for {} - {} as exclude date already exists", excludeDate, excludeDateEntity.desc, code)
      val message = alreadyAddedExcludeDateMessage(excludeDateEntity, code, excludeDate)
      throw ValidationException(message)
    }
  }

  @kotlin.jvm.Throws(ValidationException::class)
  private fun validateRemoveExcludeDate(excludeDateEntity: ExcludeDateEntity, excludeDate: LocalDate, prisonCode: String, existingExcludeDates: Set<LocalDate>) {
    if (!existingExcludeDates.contains(excludeDate)) {
      LOG.info("failed to remove exclude date - {} for {} - {} as exclude date does not exist", excludeDate, excludeDateEntity.desc, prisonCode)
      val message = nonExistentExcludeDateMessage(excludeDateEntity, prisonCode, excludeDate)
      throw ValidationException(message)
    }
  }

  private fun getPastExcludeDateMessage(excludeDateEntity: ExcludeDateEntity, code: String, excludeDate: LocalDate): String? {
    return when (excludeDateEntity) {
      ExcludeDateEntity.PRISON -> messageService.getMessage("validation.add.prison.excludedate.inpast", excludeDate.toString(), code)
      ExcludeDateEntity.SESSION_TEMPLATE -> messageService.getMessage("validation.add.session.excludedate.inpast", excludeDate.toString(), code)
    }
  }

  private fun alreadyAddedExcludeDateMessage(excludeDateEntity: ExcludeDateEntity, code: String, excludeDate: LocalDate): String? {
    return when (excludeDateEntity) {
      ExcludeDateEntity.PRISON -> messageService.getMessage("validation.add.prison.excludedate.alreadyexists", excludeDate.toString(), code)
      ExcludeDateEntity.SESSION_TEMPLATE -> messageService.getMessage("validation.add.session.excludedate.alreadyexists", excludeDate.toString(), code)
    }
  }

  private fun nonExistentExcludeDateMessage(excludeDateEntity: ExcludeDateEntity, code: String, excludeDate: LocalDate): String? {
    return when (excludeDateEntity) {
      ExcludeDateEntity.PRISON -> messageService.getMessage("validation.remove.prison.excludedate.doesnotexist", excludeDate.toString(), code)
      ExcludeDateEntity.SESSION_TEMPLATE -> messageService.getMessage("validation.remove.session.excludedate.doesnotexist", excludeDate.toString(), code)
    }
  }

  private fun getExistingExcludeDates(prison: Prison): Set<LocalDate> {
    return prison.excludeDates.stream().map { it.excludeDate }.collect(Collectors.toSet())
  }

  private fun getExistingExcludeDates(sessionTemplate: SessionTemplate): Set<LocalDate> {
    return sessionTemplate.excludeDates.stream().map { it.excludeDate }.collect(Collectors.toSet())
  }

  private fun isExcludeDateInPast(excludeDate: LocalDate): Boolean {
    return (excludeDate < LocalDate.now())
  }
}

private enum class ExcludeDateEntity(val desc: String) {
  PRISON("prison"),
  SESSION_TEMPLATE("session template"),
}
