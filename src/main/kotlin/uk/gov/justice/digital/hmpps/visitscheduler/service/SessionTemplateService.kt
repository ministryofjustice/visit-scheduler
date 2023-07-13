package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.ALL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.CURRENT_OR_FUTURE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.HISTORIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.CreateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.UpdateCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.CreateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.UpdateIncentiveGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.CreateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.UpdateLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.ItemNotFoundException
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionCategoryGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.SessionPrisonerCategory
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionIncentiveLevelGroup
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.SessionPrisonerIncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.util.function.Supplier
import kotlin.jvm.Throws

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  private val sessionIncentiveLevelGroupRepository: SessionIncentiveLevelGroupRepository,
  private val visitRepository: VisitRepository,
  private val prisonConfigService: PrisonConfigService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional(readOnly = true)
  fun getSessionTemplates(prisonCode: String, rangeType: SessionTemplateRangeType): List<SessionTemplateDto> {
    val sessionTemplates = when (rangeType) {
      ALL -> sessionTemplateRepository.findSessionTemplatesByPrisonCode(prisonCode)
      HISTORIC -> sessionTemplateRepository.findHistoricSessionTemplates(prisonCode)
      CURRENT_OR_FUTURE -> sessionTemplateRepository.findCurrentAndFutureSessionTemplates(prisonCode)
    }
    return sessionTemplates.sortedBy { it.validFromDate }.map { SessionTemplateDto(it) }
  }

  @Transactional(readOnly = true)
  fun getSessionTemplates(reference: String): SessionTemplateDto {
    return SessionTemplateDto(getSessionTemplate(reference))
  }

  @Transactional(readOnly = true)
  fun getSessionLocationGroup(reference: String): SessionLocationGroupDto {
    return SessionLocationGroupDto(getLocationGroupByReference(reference))
  }

  @Transactional(readOnly = true)
  fun getSessionLocationGroups(prisonCode: String): List<SessionLocationGroupDto> {
    return sessionLocationGroupRepository.findByPrisonCode(prisonCode).map { SessionLocationGroupDto(it) }
  }

  fun createSessionLocationGroup(createLocationSessionGroup: CreateLocationGroupDto): SessionLocationGroupDto {
    val prison = prisonConfigService.findPrisonByCode(createLocationSessionGroup.prisonCode)
    var sessionLocationGroup = SessionLocationGroup(
      prison = prison,
      prisonId = prison.id,
      name = createLocationSessionGroup.name,
    )
    sessionLocationGroup = sessionLocationGroupRepository.saveAndFlush(sessionLocationGroup)

    val sessionLocations = createLocationSessionGroup.locations.toSet().map {
      PermittedSessionLocation(
        groupId = sessionLocationGroup.id,
        sessionLocationGroup = sessionLocationGroup,
        levelOneCode = it.levelOneCode,
        levelTwoCode = it.levelTwoCode,
        levelThreeCode = it.levelThreeCode,
        levelFourCode = it.levelFourCode,
      )
    }

    sessionLocationGroup.sessionLocations.addAll(sessionLocations)

    return SessionLocationGroupDto(sessionLocationGroup)
  }

  fun updateSessionLocationGroup(reference: String, updateLocationSessionGroup: UpdateLocationGroupDto): SessionLocationGroupDto {
    val sessionLocationGroup = getLocationGroupByReference(reference)
    sessionLocationGroup.name = updateLocationSessionGroup.name
    sessionLocationGroup.sessionLocations.clear()

    val sessionLocations = updateLocationSessionGroup.locations.toSet().map {
      PermittedSessionLocation(
        groupId = sessionLocationGroup.id,
        sessionLocationGroup = sessionLocationGroup,
        levelOneCode = it.levelOneCode,
        levelTwoCode = it.levelTwoCode,
        levelThreeCode = it.levelThreeCode,
        levelFourCode = it.levelFourCode,
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
      startTime = createSessionTemplateDto.sessionTimeSlot.startTime,
      endTime = createSessionTemplateDto.sessionTimeSlot.endTime,
      validFromDate = createSessionTemplateDto.sessionDateRange.validFromDate,
      validToDate = createSessionTemplateDto.sessionDateRange.validToDate,
      visitRoom = createSessionTemplateDto.visitRoom,
      closedCapacity = createSessionTemplateDto.sessionCapacity.closed,
      openCapacity = createSessionTemplateDto.sessionCapacity.open,
      weeklyFrequency = createSessionTemplateDto.weeklyFrequency,
      dayOfWeek = createSessionTemplateDto.dayOfWeek,
      visitType = VisitType.SOCIAL,
      active = false,
    )

    createSessionTemplateDto.categoryGroupReferences?.let {
      it.toSet().forEach { ref -> sessionTemplateEntity.permittedSessionCategoryGroups.add(this.getPrisonerCategoryGroupByReference(ref)) }
    }

    createSessionTemplateDto.locationGroupReferences?.let {
      it.toSet().forEach { ref -> sessionTemplateEntity.permittedSessionLocationGroups.add(this.getLocationGroupByReference(ref)) }
    }

    createSessionTemplateDto.incentiveLevelGroupReferences?.let {
      it.toSet().forEach { ref -> sessionTemplateEntity.permittedSessionIncentiveLevelGroups.add(this.getIncentiveLevelGroupByReference(ref)) }
    }

    val sessionTemplateEntitySave = sessionTemplateRepository.saveAndFlush(sessionTemplateEntity)

    return SessionTemplateDto(
      sessionTemplateEntitySave,
    )
  }

  private fun validateUpdateSessionTemplate(reference: String, updateSessionTemplateDto: UpdateSessionTemplateDto) {
    val sessionTemplate = SessionTemplateDto(getSessionTemplate(reference))
    val hasVisits = visitRepository.hasVisitsForSessionTemplate(reference)
    validateUpdateSessionTemplateTime(sessionTemplate, updateSessionTemplateDto, hasVisits)
    validateUpdateSessionTemplateFromDate(sessionTemplate, updateSessionTemplateDto, hasVisits)
  }

  private fun validateUpdateSessionTemplateTime(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean) {
    // if a session has visits its time cannot be updated.
    if (updateSessionTemplateDto.sessionTimeSlot != null && existingSessionTemplate.sessionTimeSlot != updateSessionTemplateDto.sessionTimeSlot && hasVisits) {
      throw VSiPValidationException("Cannot update session times for ${existingSessionTemplate.reference} as there are existing visits associated with this session template!")
    }
  }

  private fun validateUpdateSessionTemplateFromDate(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean) {
    // if a session has visits from date cannot be updated.
    if (updateSessionTemplateDto.sessionDateRange != null && existingSessionTemplate.sessionDateRange.validFromDate != updateSessionTemplateDto.sessionDateRange.validFromDate && hasVisits) {
      throw VSiPValidationException("Cannot update session valid from date for ${existingSessionTemplate.reference} as there are existing visits associated with this session template!")
    }
  }

  fun updateSessionTemplate(reference: String, updateSessionTemplateDto: UpdateSessionTemplateDto): SessionTemplateDto {
    validateUpdateSessionTemplate(reference, updateSessionTemplateDto)

    with(updateSessionTemplateDto) {
      name?.let {
        sessionTemplateRepository.updateNameByReference(reference, name)
      }

      sessionTimeSlot?.let {
        sessionTemplateRepository.updateSessionTimeSlotByReference(reference, it)
      }

      sessionDateRange?.let {
        sessionTemplateRepository.updateSessionDateRangeByReference(reference, it)
      }

      sessionCapacity?.let {
        sessionTemplateRepository.updateCapacityByReference(reference, it)
      }

      weeklyFrequency?.let {
        sessionTemplateRepository.updateWeeklyFrequencyByReference(reference, weeklyFrequency)
      }
    }

    val updatedSessionTemplateEntity = getSessionTemplate(reference)

    updateSessionTemplateDto.locationGroupReferences?.let {
      updatedSessionTemplateEntity.permittedSessionLocationGroups.clear()
      it.toSet().forEach { ref -> updatedSessionTemplateEntity.permittedSessionLocationGroups.add(this.getLocationGroupByReference(ref)) }
    }

    updateSessionTemplateDto.categoryGroupReferences?.let {
      updatedSessionTemplateEntity.permittedSessionCategoryGroups.clear()
      it.toSet().forEach { ref -> updatedSessionTemplateEntity.permittedSessionCategoryGroups.add(this.getPrisonerCategoryGroupByReference(ref)) }
    }

    updateSessionTemplateDto.incentiveLevelGroupReferences?.let {
      updatedSessionTemplateEntity.permittedSessionIncentiveLevelGroups.clear()
      it.toSet().forEach { ref -> updatedSessionTemplateEntity.permittedSessionIncentiveLevelGroups.add(this.getIncentiveLevelGroupByReference(ref)) }
    }
    return SessionTemplateDto(updatedSessionTemplateEntity)
  }

  fun deleteSessionTemplate(reference: String) {
    val sessionTemplate = getSessionTemplate(reference)
    if (sessionTemplate.active) {
      throw VSiPValidationException("Cannot delete session template $reference since it is active!")
    }
    if (visitRepository.hasVisitsForSessionTemplate(reference)) {
      throw VSiPValidationException("Cannot delete session template $reference with existing visits!")
    }

    val deleted = sessionTemplateRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Session template not found : $reference")
    }
  }

  @Throws(TemplateNotFoundException::class)
  fun activateSessionTemplate(reference: String): SessionTemplateDto {
    val sessionTemplate = getSessionTemplate(reference)
    sessionTemplate.active = true
    sessionTemplateRepository.updateActiveByReference(reference, true)
    return SessionTemplateDto(sessionTemplate)
  }

  @Throws(TemplateNotFoundException::class)
  fun deActivateSessionTemplate(reference: String): SessionTemplateDto {
    val sessionTemplate = getSessionTemplate(reference)
    sessionTemplate.active = false
    sessionTemplateRepository.updateActiveByReference(reference, false)
    return SessionTemplateDto(sessionTemplate)
  }

  fun deleteSessionLocationGroup(reference: String) {
    val group = getLocationGroupByReference(reference)
    if (group.sessionTemplates.isNotEmpty()) {
      throw VSiPValidationException("Location group cannot be deleted $reference because session templates are using it!")
    }

    val deleted = sessionLocationGroupRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Session location group not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Session location group $reference was deleted!")
    }
  }

  private fun getPrisonerCategoryGroupByReference(reference: String): SessionCategoryGroup {
    return sessionCategoryGroupRepository.findByReference(reference) ?: throw ItemNotFoundException("SessionPrisonerCategory reference:$reference not found")
  }

  private fun getLocationGroupByReference(reference: String): SessionLocationGroup {
    return sessionLocationGroupRepository.findByReference(reference)
      ?: throw ItemNotFoundException("SessionLocationGroup reference:$reference not found")
  }

  private fun getSessionTemplate(reference: String): SessionTemplate {
    return sessionTemplateRepository.findByReference(reference)
      ?: throw TemplateNotFoundException("Template reference:$reference not found")
  }

  fun createSessionCategoryGroup(createCategorySessionGroup: CreateCategoryGroupDto): SessionCategoryGroupDto {
    val prison = prisonConfigService.findPrisonByCode(createCategorySessionGroup.prisonCode)
    var sessionCategoryGroup = SessionCategoryGroup(
      prison = prison,
      prisonId = prison.id,
      name = createCategorySessionGroup.name,
    )

    sessionCategoryGroup = sessionCategoryGroupRepository.saveAndFlush(sessionCategoryGroup)

    val sessionPrisonerCategories = createCategorySessionGroup.categories.toSet().map {
      SessionPrisonerCategory(
        sessionCategoryGroupId = sessionCategoryGroup.id,
        sessionCategoryGroup = sessionCategoryGroup,
        prisonerCategoryType = it,
      )
    }

    sessionCategoryGroup.sessionCategories.addAll(sessionPrisonerCategories)

    return SessionCategoryGroupDto(sessionCategoryGroup)
  }

  fun updateSessionCategoryGroup(
    reference: String,
    updateCategorySessionGroup: UpdateCategoryGroupDto,
  ): SessionCategoryGroupDto {
    val groupToUpdate = getSessionCategoryGroupEntityByReference(reference)
    groupToUpdate.name = updateCategorySessionGroup.name
    groupToUpdate.sessionCategories.clear()

    val sessionPrisonerCategorys = updateCategorySessionGroup.categories.toSet().map {
      SessionPrisonerCategory(
        sessionCategoryGroupId = groupToUpdate.id,
        sessionCategoryGroup = groupToUpdate,
        prisonerCategoryType = it,
      )
    }

    groupToUpdate.sessionCategories.addAll(sessionPrisonerCategorys)

    val updatedGroup = sessionCategoryGroupRepository.saveAndFlush(groupToUpdate)
    return SessionCategoryGroupDto(updatedGroup)
  }

  fun deleteSessionCategoryGroup(reference: String) {
    val group = getSessionCategoryGroupEntityByReference(reference)
    if (group.sessionTemplates.isNotEmpty()) {
      throw VSiPValidationException("Category group cannot be deleted $reference because session templates are using it!")
    }

    val deleted = sessionCategoryGroupRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Session category group not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Session category group $reference was deleted!")
    }
  }

  @Transactional(readOnly = true)
  fun getSessionCategoryGroup(reference: String): SessionCategoryGroupDto {
    return SessionCategoryGroupDto(getSessionCategoryGroupEntityByReference(reference))
  }

  private fun getSessionCategoryGroupEntityByReference(reference: String): SessionCategoryGroup {
    return sessionCategoryGroupRepository.findByReference(reference)
      ?: throw ItemNotFoundException("SessionCategoryGroupDto reference:$reference not found")
  }

  @Transactional(readOnly = true)
  fun getSessionCategoryGroups(prisonCode: String): List<SessionCategoryGroupDto> {
    return sessionCategoryGroupRepository.findByPrisonCode(prisonCode).map { SessionCategoryGroupDto(it) }
  }

  @Transactional(readOnly = true)
  fun getSessionIncentiveGroups(prisonCode: String): List<SessionIncentiveLevelGroupDto> {
    return sessionIncentiveLevelGroupRepository.findByPrisonCode(prisonCode).map { SessionIncentiveLevelGroupDto(it) }
  }

  @Transactional(readOnly = true)
  fun getSessionIncentiveGroup(reference: String): SessionIncentiveLevelGroupDto {
    return SessionIncentiveLevelGroupDto(getIncentiveLevelGroupByReference(reference))
  }

  private fun getIncentiveLevelGroupByReference(reference: String): SessionIncentiveLevelGroup {
    return sessionIncentiveLevelGroupRepository.findByReference(reference) ?: throw ItemNotFoundException("SessionPrisonerIncentiveLevel reference:$reference not found")
  }

  fun createSessionIncentiveGroup(createIncentiveSessionGroup: CreateIncentiveGroupDto): SessionIncentiveLevelGroupDto {
    val prison = prisonConfigService.findPrisonByCode(createIncentiveSessionGroup.prisonCode)
    var sessionIncentiveLevelGroup = SessionIncentiveLevelGroup(
      prison = prison,
      prisonId = prison.id,
      name = createIncentiveSessionGroup.name,
    )

    sessionIncentiveLevelGroup = sessionIncentiveLevelGroupRepository.saveAndFlush(sessionIncentiveLevelGroup)

    val sessionIncentiveLevels = createIncentiveSessionGroup.incentiveLevels.toSet().map {
      SessionPrisonerIncentiveLevel(
        sessionIncentiveGroupId = sessionIncentiveLevelGroup.id,
        sessionIncentiveLevelGroup = sessionIncentiveLevelGroup,
        prisonerIncentiveLevel = it,
      )
    }

    sessionIncentiveLevelGroup.sessionIncentiveLevels.addAll(sessionIncentiveLevels)
    return SessionIncentiveLevelGroupDto(sessionIncentiveLevelGroup)
  }

  fun updateSessionIncentiveGroup(
    reference: String,
    updateIncentiveSessionGroup: UpdateIncentiveGroupDto,
  ): SessionIncentiveLevelGroupDto {
    val groupToUpdate = getIncentiveLevelGroupByReference(reference)
    groupToUpdate.name = updateIncentiveSessionGroup.name
    groupToUpdate.sessionIncentiveLevels.clear()

    val sessionIncentiveLevels = updateIncentiveSessionGroup.incentiveLevels.toSet().map {
      SessionPrisonerIncentiveLevel(
        sessionIncentiveGroupId = groupToUpdate.id,
        sessionIncentiveLevelGroup = groupToUpdate,
        prisonerIncentiveLevel = it,
      )
    }

    groupToUpdate.sessionIncentiveLevels.addAll(sessionIncentiveLevels)

    val updatedGroup = sessionIncentiveLevelGroupRepository.saveAndFlush(groupToUpdate)
    return SessionIncentiveLevelGroupDto(updatedGroup)
  }

  fun deleteSessionIncentiveGroup(reference: String) {
    val group = getIncentiveLevelGroupByReference(reference)
    if (group.sessionTemplates.isNotEmpty()) {
      throw VSiPValidationException("Incentive group cannot be deleted $reference because session templates are using it!")
    }

    val deleted = sessionIncentiveLevelGroupRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Incentive group  group not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Incentive group $reference was deleted!")
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
