package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateMapper
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateUtil
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionCategoryMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionIncentiveLevelMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher
import java.time.LocalDate

@Component
class UpdateSessionTemplateValidator(
  private val visitRepository: VisitRepository,
  private val sessionLocationMatcher: SessionLocationMatcher,
  private val sessionCategoryMatcher: SessionCategoryMatcher,
  private val sessionIncentiveLevelMatcher: SessionIncentiveLevelMatcher,
  private val sessionTemplateUtil: SessionTemplateUtil,
  private val sessionTemplateMapper: SessionTemplateMapper,
) {
  fun validate(sessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto): List<String> {
    val errorMessages = mutableListOf<String>()
    val hasVisits = visitRepository.hasVisitsForSessionTemplate(sessionTemplate.reference)
    validateUpdateSessionTemplateTime(sessionTemplate, updateSessionTemplateDto, hasVisits)?.let { errorMessages.add(it) }
    validateUpdateSessionTemplateDate(sessionTemplate, updateSessionTemplateDto, hasVisits).let { errorMessages.addAll(it) }
    validateUpdateSessionTemplateWeeklyFrequency(sessionTemplate, updateSessionTemplateDto, hasVisits)?.let { errorMessages.add(it) }

    val hasFutureBookedVisits = visitRepository.hasBookedVisitsForSessionTemplate(sessionTemplate.reference, LocalDate.now())
    val updateSessionDetails = sessionTemplateMapper.getSessionDetails(sessionTemplate.reference, updateSessionTemplateDto)

    // TODO - the below block will need rewriting as it does not consider the exclude scenario
    if (sessionTemplate.includeLocationGroupType) {
      updateSessionTemplateDto.locationGroupReferences.let {
        validateUpdateSessionLocation(
          sessionTemplate,
          updateSessionDetails,
          hasFutureBookedVisits,
        )?.let { errorMessages.add(it) }
      }
    }

    if (sessionTemplate.includeCategoryGroupType) {
      updateSessionTemplateDto.categoryGroupReferences.let {
        validateUpdateSessionCategory(
          sessionTemplate,
          updateSessionDetails,
          hasFutureBookedVisits,
        )?.let { errorMessages.add(it) }
      }
    }

    if (sessionTemplate.includeIncentiveGroupType) {
      updateSessionTemplateDto.incentiveLevelGroupReferences.let {
        validateUpdateSessionIncentiveLevels(
          sessionTemplate,
          updateSessionDetails,
          hasFutureBookedVisits,
        )?.let { errorMessages.add(it) }
      }
    }
    return errorMessages.toList()
  }

  private fun validateUpdateSessionTemplateTime(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean): String? {
    // if a session has visits its time cannot be updated.
    return if (updateSessionTemplateDto.sessionTimeSlot != null && existingSessionTemplate.sessionTimeSlot != updateSessionTemplateDto.sessionTimeSlot && hasVisits) {
      "Cannot update session times for ${existingSessionTemplate.reference} as there are existing visits associated with this session template!"
    } else {
      null
    }
  }

  private fun validateUpdateSessionTemplateDate(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean): List<String> {
    val validationErrors = mutableListOf<String>()
    validateUpdateSessionTemplateFromDate(existingSessionTemplate, updateSessionTemplateDto, hasVisits)?.let {
      validationErrors.add(it)
    }

    validateUpdateSessionTemplateToDate(existingSessionTemplate, updateSessionTemplateDto)?.let {
      validationErrors.add(it)
    }

    return validationErrors
  }

  private fun validateUpdateSessionTemplateFromDate(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean): String? {
    // if a session has visits from date cannot be updated.
    return if (updateSessionTemplateDto.sessionDateRange != null && existingSessionTemplate.sessionDateRange.validFromDate != updateSessionTemplateDto.sessionDateRange.validFromDate && hasVisits) {
      return "Cannot update session valid from date for ${existingSessionTemplate.reference} as there are existing visits associated with this session template!"
    } else {
      null
    }
  }

  private fun validateUpdateSessionTemplateToDate(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto): String? {
    updateSessionTemplateDto.sessionDateRange?.let {
      val newValidToDate = it.validToDate
      val existingValidToDate = existingSessionTemplate.sessionDateRange.validToDate

      if (newValidToDate != existingValidToDate) {
        // if the new validToDate is not null or before existing validToDate
        if ((newValidToDate != null && existingValidToDate == null) || (newValidToDate != null && newValidToDate.isBefore(existingValidToDate))) {
          // check if there are any booked or reserved visits (any visit status) after the new valid to date
          if (visitRepository.hasBookedVisitsForSessionTemplate(existingSessionTemplate.reference, newValidToDate.plusDays(1))) {
            return "Cannot update session valid to date to $newValidToDate for session template - ${existingSessionTemplate.reference} as there are booked or reserved visits associated with this session template after $newValidToDate."
          }
        }
      }
    }

    return null
  }

  private fun validateUpdateSessionTemplateWeeklyFrequency(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean): String? {
    // if a session has visits weekly frequency can only be updated if the new weekly frequency is lower than current weekly frequency
    // and the new weekly frequency is a factor of the existing weekly frequency
    val newWeeklyFrequency = updateSessionTemplateDto.weeklyFrequency

    if (newWeeklyFrequency != null && (newWeeklyFrequency != existingSessionTemplate.weeklyFrequency)) {
      // if weekly frequency is being upped  and there are existing visits for the template
      // or weekly frequency is reduced to a non factor and there are existing visits for the template
      // throw a VSiPValidationException
      if ((newWeeklyFrequency > existingSessionTemplate.weeklyFrequency || existingSessionTemplate.weeklyFrequency % newWeeklyFrequency != 0) && hasVisits) {
        return "Cannot update session template weekly frequency from ${existingSessionTemplate.weeklyFrequency} to $newWeeklyFrequency for ${existingSessionTemplate.reference} as existing visits for ${existingSessionTemplate.reference} might be affected!"
      }
    }

    return null
  }

  private fun validateUpdateSessionLocation(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all locations should be accommodated post update
    if (hasFutureBookedVisits) {
      val existingSessionLocations = sessionTemplateUtil.getPermittedSessionLocations(existingSessionTemplate.permittedLocationGroups)
      val updatedSessionLocations = updateSessionDetails.permittedLocationGroups.flatMap { it.locations }.toSet()
      if (!sessionLocationMatcher.hasAllLowerOrEqualMatch(existingSessionLocations, updatedSessionLocations)) {
        return "Cannot update locations to the new location list as all existing locations in session template are not catered for."
      }
    }

    return null
  }

  private fun validateUpdateSessionCategory(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all categories should be accommodated post update
    if (hasFutureBookedVisits) {
      val existingCategories = sessionTemplateUtil.getPermittedPrisonerCategoryTypes(existingSessionTemplate.prisonerCategoryGroups)
      val updatedCategories = updateSessionDetails.prisonerCategoryGroups.flatMap { it.categories }.toSet()
      if (!sessionCategoryMatcher.hasAllMatch(existingCategories, updatedCategories)) {
        return "Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."
      }
    }

    return null
  }

  private fun validateUpdateSessionIncentiveLevels(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all categories should be accommodated post update
    if (hasFutureBookedVisits) {
      val existingIncentiveLevels = sessionTemplateUtil.getPermittedIncentiveLevels(existingSessionTemplate.prisonerIncentiveLevelGroups)
      val updatedIncentiveLevels = updateSessionDetails.prisonerIncentiveLevelGroups.flatMap { it.incentiveLevels }.toSet()
      if (!sessionIncentiveLevelMatcher.hasAllMatch(existingIncentiveLevels, updatedIncentiveLevels)) {
        return "Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."
      }
    }

    return null
  }
}
