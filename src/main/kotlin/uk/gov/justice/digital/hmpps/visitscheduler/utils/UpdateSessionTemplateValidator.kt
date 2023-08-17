package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionCategoryMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionIncentiveLevelMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher
import java.time.LocalDate

@Component
class UpdateSessionTemplateValidator(
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
  private val sessionLocationMatcher: SessionLocationMatcher,
  private val sessionCategoryMatcher: SessionCategoryMatcher,
  private val sessionIncentiveLevelMatcher: SessionIncentiveLevelMatcher,
  private val sessionTemplateUtil: SessionTemplateUtil,
  private val sessionTemplateMapper: SessionTemplateMapper,
  @Value("\${policy.session.booking-notice-period.maximum-days:28}")
  private val policyNoticeDaysMax: Long,
) {
  fun validate(sessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto): List<String> {
    val errorMessages = mutableListOf<String>()
    val hasVisits = visitRepository.hasVisitsForSessionTemplate(sessionTemplate.reference)
    validateUpdateSessionTemplateTime(sessionTemplate, updateSessionTemplateDto, hasVisits)?.let { errorMessages.add(it) }
    validateUpdateSessionTemplateDate(sessionTemplate, updateSessionTemplateDto, hasVisits).let { errorMessages.addAll(it) }
    validateUpdateSessionTemplateWeeklyFrequency(sessionTemplate, updateSessionTemplateDto, hasVisits)?.let { errorMessages.add(it) }
    validateUpdateSessionCapacity(sessionTemplate, updateSessionTemplateDto, hasVisits).let { errorMessages.addAll(it) }

    val hasFutureBookedVisits = visitRepository.hasBookedVisitsForSessionTemplate(sessionTemplate.reference, LocalDate.now())
    val updateSessionDetails = sessionTemplateMapper.getSessionDetails(sessionTemplate.reference, updateSessionTemplateDto)
    updateSessionTemplateDto.locationGroupReferences.let {
      validateUpdateSessionLocation(sessionTemplate, updateSessionDetails, hasFutureBookedVisits)?.let { errorMessages.add(it) }
    }
    updateSessionTemplateDto.categoryGroupReferences.let {
      validateUpdateSessionCategory(sessionTemplate, updateSessionDetails, hasFutureBookedVisits)?.let { errorMessages.add(it) }
    }
    updateSessionTemplateDto.incentiveLevelGroupReferences.let {
      validateUpdateSessionIncentiveLevels(sessionTemplate, updateSessionDetails, hasFutureBookedVisits)?.let { errorMessages.add(it) }
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

  private fun validateUpdateSessionCapacity(existingSessionTemplate: SessionTemplateDto, updateSessionTemplateDto: UpdateSessionTemplateDto, hasVisits: Boolean): List<String> {
    val newSessionCapacity = updateSessionTemplateDto.sessionCapacity
    val existingSessionCapacity = existingSessionTemplate.sessionCapacity
    val errorMessage = "Cannot update session template %s capacity from %d to %d for %s as its lower than minimum capacity of %d!"
    val errorMessages = mutableListOf<String>()

    if (newSessionCapacity != null && (newSessionCapacity != existingSessionTemplate.sessionCapacity)) {
      if (newSessionCapacity.closed < existingSessionCapacity.closed || newSessionCapacity.open < existingSessionCapacity.open && hasVisits) {
        // check if new capacities are lower than minimum capacity allowed for open and closed visits
        val minimumCapacityTuple = sessionTemplateRepository.findSessionTemplateMinCapacityBy(
          existingSessionTemplate.reference,
          LocalDate.now(),
          LocalDate.now().plusDays(policyNoticeDaysMax),
        )
        val minimumCapacity = sessionTemplateUtil.getMinimumSessionCapacity(minimumCapacityTuple)

        if (newSessionCapacity.closed < minimumCapacity.closed) {
          errorMessages.add(String.format(errorMessage, "closed", existingSessionCapacity.closed, newSessionCapacity.closed, existingSessionTemplate.reference, minimumCapacity.closed))
        }

        if (newSessionCapacity.open < minimumCapacity.open) {
          errorMessages.add(String.format(errorMessage, "open", existingSessionCapacity.open, newSessionCapacity.open, existingSessionTemplate.reference, minimumCapacity.open))
        }
      }
    }

    return errorMessages
  }

  private fun validateUpdateSessionLocation(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all locations should be accommodated post update
    val errorMessage = "Cannot update locations to the new location list as all existing locations in session template are not catered for."

    if (hasFutureBookedVisits) {
      val existingSessionLocations = sessionTemplateUtil.getPermittedSessionLocations(existingSessionTemplate.permittedLocationGroups)
      val updatedSessionLocations = updateSessionDetails.permittedLocationGroups.flatMap { it.locations }.toSet()
      if (!sessionLocationMatcher.hasAllLowerOrEqualMatch(existingSessionLocations, updatedSessionLocations)) {
        return errorMessage
      }
    }

    return null
  }

  private fun validateUpdateSessionCategory(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all categories should be accommodated post update
    val errorMessage = "Cannot update categories to the new category list as all existing prisoner categories in session template are not catered for."

    if (hasFutureBookedVisits) {
      val existingCategories = sessionTemplateUtil.getPermittedPrisonerCategoryTypes(existingSessionTemplate.prisonerCategoryGroups)
      val updatedCategories = updateSessionDetails.prisonerCategoryGroups.flatMap { it.categories }.toSet()
      if (!sessionCategoryMatcher.hasAllMatch(existingCategories, updatedCategories)) {
        return errorMessage
      }
    }

    return null
  }

  private fun validateUpdateSessionIncentiveLevels(existingSessionTemplate: SessionTemplateDto, updateSessionDetails: SessionDetailsDto, hasFutureBookedVisits: Boolean): String? {
    // if a session has booked visits all categories should be accommodated post update
    val errorMessage = "Cannot update incentive levels to the new incentive levels list as all existing incentive levels in session template are not catered for."

    if (hasFutureBookedVisits) {
      val existingIncentiveLevels = sessionTemplateUtil.getPermittedIncentiveLevels(existingSessionTemplate.prisonerIncentiveLevelGroups)
      val updatedIncentiveLevels = updateSessionDetails.prisonerIncentiveLevelGroups.flatMap { it.incentiveLevels }.toSet()
      if (!sessionIncentiveLevelMatcher.hasAllMatch(existingIncentiveLevels, updatedIncentiveLevels)) {
        return errorMessage
      }
    }

    return null
  }
}
