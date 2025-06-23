package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.CreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.category.SessionCategoryGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.incentive.SessionIncentiveLevelGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.SessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionCategoryGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionIncentiveLevelGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionLocationGroupRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TemplateNotFoundException

@Component
class SessionTemplateMapper(
  private val sessionLocationGroupRepository: SessionLocationGroupRepository,
  private val sessionCategoryGroupRepository: SessionCategoryGroupRepository,
  private val sessionIncentiveLevelGroupRepository: SessionIncentiveLevelGroupRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
) {
  fun getSessionDetails(createSessionTemplateDto: CreateSessionTemplateDto): SessionDetailsDto = SessionDetailsDto(
    prisonCode = createSessionTemplateDto.prisonCode,
    sessionTimeSlot = createSessionTemplateDto.sessionTimeSlot,
    sessionDateRange = createSessionTemplateDto.sessionDateRange,
    sessionCapacity = createSessionTemplateDto.sessionCapacity,
    dayOfWeek = createSessionTemplateDto.dayOfWeek,
    weeklyFrequency = createSessionTemplateDto.weeklyFrequency,
    includeLocationGroupType = createSessionTemplateDto.includeLocationGroupType,
    permittedLocationGroups = getSessionLocationGroups(createSessionTemplateDto.locationGroupReferences ?: emptyList()),
    includeCategoryGroupType = createSessionTemplateDto.includeCategoryGroupType,
    prisonerCategoryGroups = getSessionCategoryGroups(createSessionTemplateDto.categoryGroupReferences ?: emptyList()),
    includeIncentiveGroupType = createSessionTemplateDto.includeIncentiveGroupType,
    prisonerIncentiveLevelGroups = getSessionIncentiveLevelGroups(createSessionTemplateDto.incentiveLevelGroupReferences ?: emptyList()),
  )

  fun getSessionDetails(reference: String, updateSessionTemplateDetailsDto: UpdateSessionTemplateDetailsDto): SessionDetailsDto {
    val sessionTemplate = sessionTemplateRepository.findByReference(reference)

    if (sessionTemplate != null) {
      return with(updateSessionTemplateDetailsDto) {
        SessionDetailsDto(
          prisonCode = sessionTemplate.prison.code,
          sessionTimeSlot = sessionTimeSlot ?: SessionTimeSlotDto(startTime = sessionTemplate.startTime, endTime = sessionTemplate.endTime),
          sessionDateRange = sessionDateRange ?: SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate, validToDate = sessionTemplate.validToDate),
          sessionCapacity = sessionCapacity ?: SessionCapacityDto(open = sessionTemplate.openCapacity, closed = sessionTemplate.closedCapacity),
          dayOfWeek = sessionTemplate.dayOfWeek,
          weeklyFrequency = weeklyFrequency ?: sessionTemplate.weeklyFrequency,
          includeLocationGroupType = updateSessionTemplateDetailsDto.includeLocationGroupType ?: sessionTemplate.includeLocationGroupType,
          permittedLocationGroups = getSessionLocationGroups(locationGroupReferences, sessionTemplate),
          includeCategoryGroupType = updateSessionTemplateDetailsDto.includeCategoryGroupType ?: sessionTemplate.includeCategoryGroupType,
          prisonerCategoryGroups = getSessionCategoryGroups(categoryGroupReferences, sessionTemplate),
          includeIncentiveGroupType = updateSessionTemplateDetailsDto.includeIncentiveGroupType ?: sessionTemplate.includeIncentiveGroupType,
          prisonerIncentiveLevelGroups = getSessionIncentiveLevelGroups(incentiveLevelGroupReferences, sessionTemplate),
        )
      }
    } else {
      throw TemplateNotFoundException("Template reference:$reference not found")
    }
  }

  private fun getSessionLocationGroups(locationGroupReferences: List<String>): List<SessionLocationGroupDto> {
    val locationGroups = mutableListOf<SessionLocationGroupDto>()
    if (locationGroupReferences.isNotEmpty()) {
      locationGroupReferences.toSet().forEach { locationGroupReference ->
        sessionLocationGroupRepository.findByReference(locationGroupReference)?.let {
          locationGroups.add(SessionLocationGroupDto(it))
        }
      }
    }

    return locationGroups
  }

  private fun getSessionLocationGroups(locationGroupReferences: List<String>?, sessionTemplate: SessionTemplate): List<SessionLocationGroupDto> = if (locationGroupReferences != null) {
    getSessionLocationGroups(locationGroupReferences)
  } else {
    sessionTemplate.permittedSessionLocationGroups.stream().map { SessionLocationGroupDto(it) }.toList()
  }

  private fun getSessionCategoryGroups(categoryGroupReferences: List<String>): List<SessionCategoryGroupDto> {
    val categoryGroups = mutableListOf<SessionCategoryGroupDto>()
    if (categoryGroupReferences.isNotEmpty()) {
      categoryGroupReferences.toSet().forEach { categoryGroupReference ->
        sessionCategoryGroupRepository.findByReference(categoryGroupReference)?.let {
          categoryGroups.add(SessionCategoryGroupDto(it))
        }
      }
    }

    return categoryGroups
  }

  private fun getSessionCategoryGroups(categoryGroupReferences: List<String>?, sessionTemplate: SessionTemplate): List<SessionCategoryGroupDto> = if (categoryGroupReferences != null) {
    getSessionCategoryGroups(categoryGroupReferences)
  } else {
    sessionTemplate.permittedSessionCategoryGroups.stream().map { SessionCategoryGroupDto(it) }.toList()
  }

  private fun getSessionIncentiveLevelGroups(incentiveLevelGroupReferences: List<String>): List<SessionIncentiveLevelGroupDto> {
    val incentiveLevelGroups = mutableListOf<SessionIncentiveLevelGroupDto>()
    if (incentiveLevelGroupReferences.isNotEmpty()) {
      incentiveLevelGroupReferences.toSet().forEach { incentiveLevelGroupReference ->
        sessionIncentiveLevelGroupRepository.findByReference(incentiveLevelGroupReference)?.let {
          incentiveLevelGroups.add(SessionIncentiveLevelGroupDto(it))
        }
      }
    }

    return incentiveLevelGroups
  }

  private fun getSessionIncentiveLevelGroups(incentiveLevelGroupReferences: List<String>?, sessionTemplate: SessionTemplate): List<SessionIncentiveLevelGroupDto> = if (incentiveLevelGroupReferences != null) {
    getSessionIncentiveLevelGroups(incentiveLevelGroupReferences)
  } else {
    sessionTemplate.permittedSessionIncentiveLevelGroups.stream().map { SessionIncentiveLevelGroupDto(it) }.toList()
  }
}
