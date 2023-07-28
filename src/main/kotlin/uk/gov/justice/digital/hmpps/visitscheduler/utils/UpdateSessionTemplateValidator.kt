package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.UpdateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate

@Component
class UpdateSessionTemplateValidator(
  private val visitRepository: VisitRepository,
  private val sessionTemplateRepository: SessionTemplateRepository,
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
        val emptyResults = minimumCapacityTuple.get(0) == null
        val minOpenCapacity = if (emptyResults) 0 else (minimumCapacityTuple.get(0) as Long).toInt()
        val minClosedCapacity = if (emptyResults) 0 else (minimumCapacityTuple.get(1) as Long).toInt()

        if (newSessionCapacity.closed < minClosedCapacity) {
          errorMessages.add(String.format(errorMessage, "closed", existingSessionCapacity.closed, newSessionCapacity.closed, existingSessionTemplate.reference, minClosedCapacity))
        }

        if (newSessionCapacity.open < minOpenCapacity) {
          errorMessages.add(String.format(errorMessage, "open", existingSessionCapacity.open, newSessionCapacity.open, existingSessionTemplate.reference, minOpenCapacity))
        }
      }
    }

    return errorMessages
  }
}
