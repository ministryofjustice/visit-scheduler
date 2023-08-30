package uk.gov.justice.digital.hmpps.visitscheduler.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.ALL
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.CURRENT_OR_FUTURE
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.SessionTemplateRangeType.HISTORIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.RequestSessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitStatsDto
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitCountsByDate
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
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateComparator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateMapper
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateUtil
import uk.gov.justice.digital.hmpps.visitscheduler.utils.UpdateSessionTemplateValidator
import uk.gov.justice.digital.hmpps.visitscheduler.utils.validators.SessionTemplateVisitMoveValidator
import java.time.LocalDate
import java.util.function.Supplier

@Service
@Transactional
class SessionTemplateService(
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  private val sessionIncentiveLevelGroupRepository: SessionIncentiveLevelGroupRepository,
  private val visitRepository: VisitRepository,
  private val prisonConfigService: PrisonConfigService,
  private val updateSessionTemplateValidator: UpdateSessionTemplateValidator,
  private val sessionTemplateComparator: SessionTemplateComparator,
  private val sessionTemplateMapper: SessionTemplateMapper,
  private val sessionTemplateUtil: SessionTemplateUtil,
  private val visitMoveValidator: SessionTemplateVisitMoveValidator,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
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
    val sessionLocationGroup = sessionLocationGroupRepository.saveAndFlush(
      SessionLocationGroup(
        prison = prison,
        prisonId = prison.id,
        name = createLocationSessionGroup.name,
      ),
    )

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

  fun updateSessionTemplate(reference: String, updateSessionTemplateDto: UpdateSessionTemplateDto): SessionTemplateDto {
    val existingSessionTemplate = SessionTemplateDto(getSessionTemplate(reference))
    val errorMessages = updateSessionTemplateValidator.validate(existingSessionTemplate, updateSessionTemplateDto)
    if (errorMessages.isNotEmpty()) {
      throw VSiPValidationException(errorMessages.toTypedArray())
    }

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

      visitRoom?.let {
        sessionTemplateRepository.updateVisitRoomByReference(reference, it)
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
      throw VSiPValidationException(arrayOf("Cannot delete session template $reference since it is active!"))
    }
    if (visitRepository.hasVisitsForSessionTemplate(reference)) {
      throw VSiPValidationException(arrayOf("Cannot delete session template $reference with existing visits!"))
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
      throw VSiPValidationException(arrayOf("Location group cannot be deleted $reference because session templates are using it!"))
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
    val sessionCategoryGroup = sessionCategoryGroupRepository.saveAndFlush(
      SessionCategoryGroup(
        prison = prison,
        prisonId = prison.id,
        name = createCategorySessionGroup.name,
      ),
    )

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

    val sessionPrisonerCategories = updateCategorySessionGroup.categories.toSet().map {
      SessionPrisonerCategory(
        sessionCategoryGroupId = groupToUpdate.id,
        sessionCategoryGroup = groupToUpdate,
        prisonerCategoryType = it,
      )
    }

    groupToUpdate.sessionCategories.addAll(sessionPrisonerCategories)

    val updatedGroup = sessionCategoryGroupRepository.saveAndFlush(groupToUpdate)
    return SessionCategoryGroupDto(updatedGroup)
  }

  fun deleteSessionCategoryGroup(reference: String) {
    val group = getSessionCategoryGroupEntityByReference(reference)
    if (group.sessionTemplates.isNotEmpty()) {
      throw VSiPValidationException(arrayOf("Category group cannot be deleted $reference because session templates are using it!"))
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
    val sessionIncentiveLevelGroup = sessionIncentiveLevelGroupRepository.saveAndFlush(
      SessionIncentiveLevelGroup(
        prison = prison,
        prisonId = prison.id,
        name = createIncentiveSessionGroup.name,
      ),
    )

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
      throw VSiPValidationException(arrayOf("Incentive group cannot be deleted $reference because session templates are using it!"))
    }

    val deleted = sessionIncentiveLevelGroupRepository.deleteByReference(reference)
    if (deleted == 0) {
      throw ItemNotFoundException("Incentive group  group not found : $reference")
    }
    if (deleted > 1) {
      throw java.lang.IllegalStateException("More than one Incentive group $reference was deleted!")
    }
  }

  fun getSessionTemplateVisitStats(
    reference: String,
    requestSessionTemplateVisitStatsDto: RequestSessionTemplateVisitStatsDto,
  ): SessionTemplateVisitStatsDto {
    val visitsToDate = LocalDate.now().plusDays(policyNoticeDaysMax)

    val minimumCapacityTuple = this.sessionTemplateRepository.findSessionTemplateMinCapacityBy(reference, requestSessionTemplateVisitStatsDto.visitsFromDate, visitsToDate)
    val sessionCapacity = sessionTemplateUtil.getMinimumSessionCapacity(minimumCapacityTuple)

    val visitCountsByDate = this.sessionTemplateRepository.getVisitCountsByDate(reference, requestSessionTemplateVisitStatsDto.visitsFromDate, visitsToDate)
    val visitCountsList = getVisitCountsList(visitCountsByDate)

    val visitCount = this.sessionTemplateRepository.getVisitCount(reference, requestSessionTemplateVisitStatsDto.visitsFromDate, visitsToDate)
    return SessionTemplateVisitStatsDto(sessionCapacity, visitCount, visitCountsList)
  }

  fun getVisitCountsList(visitCountsByDate: List<VisitCountsByDate>): MutableList<SessionTemplateVisitCountsDto> {
    val visitCountsList = mutableListOf<SessionTemplateVisitCountsDto>()
    val visitCountsByDateMap = visitCountsByDate.groupBy { it.visitDate }
    visitCountsByDateMap.entries.forEach { entry ->
      var openCount = 0
      var closedCount = 0
      entry.value.forEach {
        if (it.visitRestriction == VisitRestriction.OPEN) openCount = it.visitCount else closedCount = it.visitCount
      }

      visitCountsList.add(SessionTemplateVisitCountsDto(visitDate = entry.key, SessionCapacityDto(closed = closedCount, open = openCount)))
    }

    return visitCountsList
  }

  fun hasMatchingSessionTemplates(
    createSessionTemplateDto: CreateSessionTemplateDto,
  ): List<String> {
    val toBeCreatedSessionDetails = sessionTemplateMapper.getSessionDetails(createSessionTemplateDto)
    return hasMatchingSessionTemplates(toBeCreatedSessionDetails)
  }

  fun hasMatchingSessionTemplates(
    reference: String,
    updateSessionTemplateDto: UpdateSessionTemplateDto,
  ): List<String> {
    val toBeUpdatedSessionDetails = sessionTemplateMapper.getSessionDetails(reference, updateSessionTemplateDto)
    return hasMatchingSessionTemplates(toBeUpdatedSessionDetails, reference)
  }

  private fun hasMatchingSessionTemplates(newSessionDetails: SessionDetailsDto, existingReference: String? = null): List<String> {
    val overlappingSessions = mutableListOf<String>()
    val existingSessionTemplates = getSessionTemplates(newSessionDetails.prisonCode, CURRENT_OR_FUTURE)
    existingSessionTemplates.stream().filter { it.reference != existingReference }.forEach {
      val existingSessionDetails = SessionDetailsDto(it)
      if (sessionTemplateComparator.hasOverlap(newSessionDetails, existingSessionDetails)) {
        overlappingSessions.add(it.reference)
      }
    }

    return overlappingSessions
  }

  fun moveSessionTemplateVisits(fromSessionTemplateReference: String, toSessionTemplateReference: String, fromDate: LocalDate): Int {
    val fromSessionTemplate = SessionTemplateDto(getSessionTemplate(fromSessionTemplateReference))
    val toSessionTemplate = SessionTemplateDto(getSessionTemplate(toSessionTemplateReference))
    // validate move before updating session template reference
    validateMoveSessionTemplateVisits(fromSessionTemplate, toSessionTemplate, fromDate)

    return if (fromSessionTemplate.sessionTimeSlot == toSessionTemplate.sessionTimeSlot) {
      visitRepository.updateVisitSessionTemplateReference(existingSessionTemplateReference = fromSessionTemplateReference, newSessionTemplateReference = toSessionTemplateReference, fromDate)
    } else {
      visitRepository.updateVisitSessionTemplateReference(existingSessionTemplateReference = fromSessionTemplateReference, newSessionTemplateReference = toSessionTemplateReference, fromDate, toSessionTemplate.sessionTimeSlot.startTime, toSessionTemplate.sessionTimeSlot.endTime)
    }
  }

  @Throws(VSiPValidationException::class)
  fun validateMoveSessionTemplateVisits(
    fromSessionTemplate: SessionTemplateDto,
    toSessionTemplate: SessionTemplateDto,
    fromDate: LocalDate,
  ) {
    val fromSessionTemplateVisitStats = getSessionTemplateVisitStats(fromSessionTemplate.reference, RequestSessionTemplateVisitStatsDto(fromDate))
    val toSessionTemplateVisitStats = getSessionTemplateVisitStats(toSessionTemplate.reference, RequestSessionTemplateVisitStatsDto(fromDate))

    visitMoveValidator.validateMoveSessionTemplateVisits(fromSessionTemplate, fromSessionTemplateVisitStats, toSessionTemplate, toSessionTemplateVisitStats, fromDate)
  }
}

class TemplateNotFoundException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<TemplateNotFoundException> {
  override fun get(): TemplateNotFoundException {
    return TemplateNotFoundException(message, cause)
  }
}
