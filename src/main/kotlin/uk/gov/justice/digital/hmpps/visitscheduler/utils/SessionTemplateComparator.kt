package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionCategoryMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionIncentiveLevelMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher

@Component
class SessionTemplateComparator(
  private val sessionDatesUtil: SessionDatesUtil,
  private val sessionLocationMatcher: SessionLocationMatcher,
  private val sessionCategoryMatcher: SessionCategoryMatcher,
  private val sessionIncentiveLevelMatcher: SessionIncentiveLevelMatcher,
  private val sessionTemplateUtil: SessionTemplateUtil,
) {

  private fun hasOverlappingDates(
    newSessionDetails: SessionDetailsDto,
    existingSessionDetails: SessionDetailsDto,
  ): Boolean {
    val newFromDate = newSessionDetails.sessionDateRange.validFromDate
    val existingFromDate = existingSessionDetails.sessionDateRange.validFromDate
    val newToDate = newSessionDetails.sessionDateRange.validToDate ?: newFromDate.plusYears(500)
    val existingToDate = existingSessionDetails.sessionDateRange.validToDate ?: newSessionDetails.sessionDateRange.validToDate?.plusDays(1) ?: newToDate

    return (
      // new from date between existing from and to dates
      (newFromDate in existingFromDate..existingToDate) ||
        // new to date between existing from and to dates
        (newToDate in existingFromDate..existingToDate) ||
        // new from date less than existing from date and new to date greater than existing to date
        (newFromDate <= existingFromDate && newToDate >= existingToDate)
      )
  }

  private fun hasOverlappingTimes(
    newSessionDetails: SessionDetailsDto,
    existingSessionDetails: SessionDetailsDto,
  ): Boolean {
    val newStartTime = newSessionDetails.sessionTimeSlot.startTime
    val existingStartTime = existingSessionDetails.sessionTimeSlot.startTime
    val newEndTime = newSessionDetails.sessionTimeSlot.endTime
    val existingEndTime = existingSessionDetails.sessionTimeSlot.endTime

    return (
      // new start time between existing start and end times
      (newStartTime in existingStartTime..existingEndTime) ||
        // new end time between existing start and end times
        (newEndTime in existingStartTime..existingEndTime) ||
        // new start time less than existing start time and new end time greater than existing end time
        (newStartTime <= existingStartTime && newEndTime >= existingEndTime)
      )
  }

  private fun hasOverlappingWeeklyFrequency(
    newSessionDetails: SessionDetailsDto,
    existingSessionDetails: SessionDetailsDto,
  ): Boolean {
    val newWeeklyFrequency = newSessionDetails.weeklyFrequency
    val existingWeeklyFrequency = existingSessionDetails.weeklyFrequency
    val newFromDate = newSessionDetails.sessionDateRange.validFromDate
    val existingFromDate = existingSessionDetails.sessionDateRange.validFromDate

    var hasOverlappingFrequency = (newWeeklyFrequency == existingWeeklyFrequency && newWeeklyFrequency == 1) ||
      (newWeeklyFrequency != existingWeeklyFrequency)

    // if the sessions do not fall on the same week and weeklyFrequency is the same dates will not overlap
    if (!hasOverlappingFrequency) {
      hasOverlappingFrequency = if (newFromDate >= existingFromDate) {
        !sessionDatesUtil.isWeeklySkipDate(newFromDate, existingFromDate, existingSessionDetails.weeklyFrequency)
      } else {
        !sessionDatesUtil.isWeeklySkipDate(existingFromDate, newFromDate, newSessionDetails.weeklyFrequency)
      }
    }

    return hasOverlappingFrequency
  }

  private fun hasOverlappingPrisonerGroups(
    newSessionDetails: SessionDetailsDto,
    existingSessionDetails: SessionDetailsDto,
  ): Boolean {
    val hasOverlappingPrisonerGroups = isSessionAvailableToAll(newSessionDetails) || isSessionAvailableToAll(existingSessionDetails)
    val isSessionAvailableToSameLocationGroup = hasCommonSessionLocations(newSessionDetails, existingSessionDetails)
    val isSessionAvailableToSameCategoryGroup = hasCommonCategories(newSessionDetails, existingSessionDetails)
    val isSessionAvailableToSameIncentiveGroup = hasCommonIncentiveLevels(newSessionDetails, existingSessionDetails)

    if (!hasOverlappingPrisonerGroups) {
      // if any one is false then overlap is false
      if (!isSessionAvailableToSameLocationGroup || !isSessionAvailableToSameCategoryGroup || !isSessionAvailableToSameIncentiveGroup) {
        return false
      }
    }

    return true
  }

  private fun isSessionAvailableToAll(sessionDetails: SessionDetailsDto): Boolean {
    return sessionDetails.permittedLocationGroups.isEmpty() &&
      sessionDetails.prisonerCategoryGroups.isEmpty() &&
      sessionDetails.prisonerIncentiveLevelGroups.isEmpty()
  }

  private fun hasCommonSessionLocations(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    val existingSessionLocations = sessionTemplateUtil.getPermittedSessionLocations(existingSessionDetails.permittedLocationGroups)
    val newSessionLocations = sessionTemplateUtil.getPermittedSessionLocations(newSessionDetails.permittedLocationGroups)

    return sessionLocationMatcher.hasAnyMatch(newSessionLocations, existingSessionLocations) ||
      sessionLocationMatcher.hasAnyMatch(existingSessionLocations, newSessionLocations)
  }

  private fun hasCommonCategories(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    val existingSessionCategoryTypes = sessionTemplateUtil.getPermittedPrisonerCategoryTypes(existingSessionDetails.prisonerCategoryGroups)
    val newSessionCategoryTypes = sessionTemplateUtil.getPermittedPrisonerCategoryTypes(newSessionDetails.prisonerCategoryGroups)

    return sessionCategoryMatcher.hasAnyMatch(newSessionCategoryTypes, existingSessionCategoryTypes) ||
      sessionCategoryMatcher.hasAnyMatch(existingSessionCategoryTypes, newSessionCategoryTypes)
  }

  private fun hasCommonIncentiveLevels(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    val existingSessionIncentiveLevels = sessionTemplateUtil.getPermittedIncentiveLevels(existingSessionDetails.prisonerIncentiveLevelGroups)
    val newSessionIncentiveLevels = sessionTemplateUtil.getPermittedIncentiveLevels(newSessionDetails.prisonerIncentiveLevelGroups)

    return sessionIncentiveLevelMatcher.hasAnyMatch(newSessionIncentiveLevels, existingSessionIncentiveLevels) ||
      sessionIncentiveLevelMatcher.hasAnyMatch(existingSessionIncentiveLevels, newSessionIncentiveLevels)
  }

  fun hasOverlap(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    return (
      (newSessionDetails.dayOfWeek == existingSessionDetails.dayOfWeek) &&
        hasOverlappingDates(newSessionDetails, existingSessionDetails) &&
        hasOverlappingTimes(newSessionDetails, existingSessionDetails) &&
        hasOverlappingWeeklyFrequency(newSessionDetails, existingSessionDetails) &&
        hasOverlappingPrisonerGroups(newSessionDetails, existingSessionDetails)
      )
  }
}
