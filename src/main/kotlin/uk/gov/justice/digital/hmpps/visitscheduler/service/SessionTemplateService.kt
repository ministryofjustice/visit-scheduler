package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import java.util.function.Supplier

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val prisonConfigService: PrisonConfigService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getSessionTemplates(): List<SessionTemplateDto> {
    return sessionTemplateRepository.findAll().sortedBy { it.validFromDate }.map { SessionTemplateDto(it) }
  }

  fun getSessionTemplates(sessionTemplateId: Long): SessionTemplateDto {
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }

  fun getSessionLocationGroup(prisonId: String): List<SessionLocationGroupDto> {
    return sessionLocationGroupRepository.findByPrisonCode(prisonId).map { SessionLocationGroupDto(it) }
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
    val sessionLocationGroup = sessionLocationGroupRepository.findByReference(reference)
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
    val sessionTemplateId = 1L
    // TODO this is just for the spike
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }

  fun updateSessionTemplate(updateSessionTemplateDto: UpdateSessionTemplateDto): SessionTemplateDto {
    val sessionTemplateId = 1L
    // TODO this is just for the spike
    return sessionTemplateRepository.findById(sessionTemplateId).map { SessionTemplateDto(it) }
      .orElseThrow(TemplateNotFoundException("Template id $sessionTemplateId not found"))
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}
