package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.function.Supplier

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val prisonConfigService: PrisonConfigService
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSessionTemplates(prisonCode: String, dayOfWeek: DayOfWeek?, rangeStartDate: LocalDate?, rangeEndDate: LocalDate?): List<SessionTemplateDto> {

    val sessionTemplates = sessionTemplateRepository.findValidSessionTemplatesBy(
      prisonCode = prisonCode,
      rangeStartDate = rangeStartDate,
      rangeEndDate = rangeEndDate,
      dayOfWeek = dayOfWeek
    )

    return sessionTemplates.sortedBy { it.validFromDate }.map { SessionTemplateDto(it) }
  }

  fun getSessionTemplates(reference: String): SessionTemplateDto {
    return SessionTemplateDto(getSessionTemplate(reference))
  }

  fun getSessionLocationGroupByReference(reference: String): SessionLocationGroupDto {
    return SessionLocationGroupDto(getLocationGroupByReference(reference))
  }

  fun getSessionLocationGroup(prisonId: String): List<SessionLocationGroupDto> {
    return sessionLocationGroupRepository.findByPrisonCode(prisonId).map { SessionLocationGroupDto(it) }
  }

  private fun getLocationGroupByReference(reference: String): SessionLocationGroup {
    val sessionLocationGroup = sessionLocationGroupRepository.findByReference(reference)
      ?: throw ItemNotFoundException("SessionLocationGroup reference:$reference not found")
    return sessionLocationGroup
  }

  private fun getSessionTemplate(reference: String): SessionTemplate {
    val sessionTemplate = sessionTemplateRepository.findByReference(reference)
      ?: throw TemplateNotFoundException("Template reference:$reference not found")
    return sessionTemplate
  }

  fun createSessionLocationGroup(createLocationSessionGroup: CreateLocationGroupDto): SessionLocationGroupDto {

    val prison = prisonConfigService.findPrisonByCode(createLocationSessionGroup.prisonCode)
    val sessionLocationGroup = SessionLocationGroup(
      prison = prison,
      prisonId = prison.id,
      name = createLocationSessionGroup.name
    )

    val sessionLocations = createLocationSessionGroup.locations.map {
      PermittedSessionLocation(
        groupId = sessionLocationGroup.id,
        sessionLocationGroup = sessionLocationGroup,
        levelOneCode = it.levelOneCode,
        levelTwoCode = it.levelTwoCode,
        levelThreeCode = it.levelThreeCode,
        levelFourCode = it.levelFourCode
      )
    }

    sessionLocationGroup.sessionLocations.addAll(sessionLocations)

    val saveSessionLocationGroup = sessionLocationGroupRepository.saveAndFlush(sessionLocationGroup)
    return SessionLocationGroupDto(saveSessionLocationGroup)
  }

  fun updateSessionLocationGroup(reference: String, updateLocationSessionGroup: UpdateLocationGroupDto): SessionLocationGroupDto {
    val sessionLocationGroup = getLocationGroupByReference(reference)
    sessionLocationGroup.name = updateLocationSessionGroup.name
    sessionLocationGroup.sessionLocations.clear()

    val sessionLocations = updateLocationSessionGroup.locations.map {
      PermittedSessionLocation(
        groupId = sessionLocationGroup.id,
        sessionLocationGroup = sessionLocationGroup,
        levelOneCode = it.levelOneCode,
        levelTwoCode = it.levelTwoCode,
        levelThreeCode = it.levelThreeCode,
        levelFourCode = it.levelFourCode
      )
    }

    sessionLocationGroup.sessionLocations.addAll(sessionLocations)
    val saveSessionLocationGroup = sessionLocationGroupRepository.saveAndFlush(sessionLocationGroup)
    return SessionLocationGroupDto(saveSessionLocationGroup)
  }

  fun createSessionTemplate(createSessionTemplateDto: CreateSessionTemplateDto): SessionTemplateDto {
    log.info("Creating session template for prison")

    val prison = prisonConfigService.findPrisonByCode(createSessionTemplateDto.prisonCode)

    val sessionTemplateEntity = SessionTemplate(
      prisonId = prison.id,
      prison = prison,
      name = createSessionTemplateDto.name,
      startTime = createSessionTemplateDto.startTime,
      endTime = createSessionTemplateDto.endTime,
      validFromDate = createSessionTemplateDto.validFromDate,
      validToDate = createSessionTemplateDto.validToDate,
      visitRoom = createSessionTemplateDto.visitRoom,
      closedCapacity = createSessionTemplateDto.closedCapacity,
      openCapacity = createSessionTemplateDto.openCapacity,
      biWeekly = createSessionTemplateDto.biWeekly,
      enhanced = createSessionTemplateDto.enhanced,
      dayOfWeek = createSessionTemplateDto.dayOfWeek,
      visitType = VisitType.SOCIAL
    )

    createSessionTemplateDto.locationGroupReferences?.let {
      it.forEach { ref -> sessionTemplateEntity.permittedSessionGroups.add(this.getLocationGroupByReference(ref)) }
    }

    val sessionTemplateEntitySave = sessionTemplateRepository.saveAndFlush(sessionTemplateEntity)

    return SessionTemplateDto(
      sessionTemplateEntitySave
    )
  }

  fun updateSessionTemplate(reference: String, updateSessionTemplateDto: UpdateSessionTemplateDto): SessionTemplateDto {

    with(updateSessionTemplateDto) {
      name?.let {
        sessionTemplateRepository.updateNameByReference(reference, name)
      }

      startTime?.let {
        sessionTemplateRepository.updateStartTimeByReference(reference, startTime)
      }

      endTime?.let {
        sessionTemplateRepository.updateEndTimeByReference(reference, endTime)
      }

      validFromDate?.let {
        sessionTemplateRepository.updateValidFromDateByReference(reference, validFromDate)
      }

      validToDate?.let {
        sessionTemplateRepository.updateValidToDateByReference(reference, validToDate)
      }

      closedCapacity?.let {
        sessionTemplateRepository.updateClosedCapacityByReference(reference, closedCapacity)
      }

      openCapacity?.let {
        sessionTemplateRepository.updateOpenCapacityByReference(reference, openCapacity)
      }

      enhanced?.let {
        sessionTemplateRepository.updateEnhancedByReference(reference, enhanced)
      }

      biWeekly?.let {
        sessionTemplateRepository.updateBiWeeklyByReference(reference, biWeekly)
      }
    }

    val updatedSessionTemplateEntity = getSessionTemplate(reference)

    updateSessionTemplateDto.locationGroupReferences?.let {
      it.forEach { ref -> updatedSessionTemplateEntity.permittedSessionGroups.add(this.getLocationGroupByReference(ref)) }
    }

    return SessionTemplateDto(updatedSessionTemplateEntity)
  }

  fun deleteSessionTemplates(reference: String) {
    val deleted = sessionTemplateRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Session template not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Session Template $reference was deleted!")
    }
  }

  fun deleteSessionLocationGroup(reference: String) {

    val group = getLocationGroupByReference(reference)
    if (group.sessionTemplates.isNotEmpty()) {
      throw VSiPValidationException("Group cannot be deleted $reference because session templates are using it!")
    }

    val deleted = sessionLocationGroupRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Session location group not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Session location group $reference was deleted!")
    }
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}
