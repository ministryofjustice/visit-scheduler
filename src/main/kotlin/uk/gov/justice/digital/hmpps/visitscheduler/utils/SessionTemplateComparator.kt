package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.location.PermittedSessionLocationDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.category.PrisonerCategoryType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import java.util.*
import java.util.function.BiPredicate
import java.util.stream.Collectors

@Component
class SessionTemplateComparator(private val sessionDatesUtil: SessionDatesUtil) {

  private val validSessionLocationMatch = object : BiPredicate<String?, String?> {
    override fun test(permittedSessionLevel: String?, prisonerLevel: String?): Boolean {
      permittedSessionLevel?.let {
        return it == prisonerLevel
      }
      // If no prison level then match
      return true
    }
  }

  private val multipleLocationMatcher =
    BiPredicate<PermittedSessionLocationDto, Set<PermittedSessionLocationDto>> { primarySessionLocation, comparedSessionLocations ->
      comparedSessionLocations.stream().anyMatch { comparedSessionLocation ->
        validSessionLocationMatch.test(primarySessionLocation.levelOneCode, comparedSessionLocation.levelOneCode)
          .and(validSessionLocationMatch.test(primarySessionLocation.levelTwoCode, comparedSessionLocation.levelTwoCode))
          .and(validSessionLocationMatch.test(primarySessionLocation.levelThreeCode, comparedSessionLocation.levelThreeCode))
          .and(validSessionLocationMatch.test(primarySessionLocation.levelFourCode, comparedSessionLocation.levelFourCode))
      }
    }

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
    return if (newSessionDetails.permittedLocationGroups.isEmpty() || existingSessionDetails.permittedLocationGroups.isEmpty()) {
      true
    } else if (newSessionDetails.permittedLocationGroups.isNotEmpty() && existingSessionDetails.permittedLocationGroups.isNotEmpty()) {
      val existingSessionLocations = getPermittedSessionLocationDtos(existingSessionDetails)
      val newSessionLocations = getPermittedSessionLocationDtos(newSessionDetails)

      hasLocationMatch(newSessionLocations, existingSessionLocations)
    } else {
      false
    }
  }

  private fun hasCommonCategories(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    return if (newSessionDetails.prisonerCategoryGroups.isEmpty() || existingSessionDetails.prisonerCategoryGroups.isEmpty()) {
      true
    } else if (newSessionDetails.prisonerCategoryGroups.isNotEmpty() && existingSessionDetails.prisonerCategoryGroups.isNotEmpty()) {
      val existingSessionCategoryTypes = getPermittedPrisonerCategoryTypes(existingSessionDetails)
      val newSessionCategoryTypes = getPermittedPrisonerCategoryTypes(newSessionDetails)

      existingSessionCategoryTypes.stream().anyMatch { newSessionCategoryTypes.contains(it) }
    } else {
      false
    }
  }

  private fun hasCommonIncentiveLevels(newSessionDetails: SessionDetailsDto, existingSessionDetails: SessionDetailsDto): Boolean {
    return if (newSessionDetails.prisonerIncentiveLevelGroups.isEmpty() || existingSessionDetails.prisonerIncentiveLevelGroups.isEmpty()) {
      true
    } else if (newSessionDetails.prisonerIncentiveLevelGroups.isNotEmpty() && existingSessionDetails.prisonerIncentiveLevelGroups.isNotEmpty()) {
      val existingSessionIncentiveLevels = getPermittedIncentiveLevels(existingSessionDetails)
      val newSessionIncentiveLevels = getPermittedIncentiveLevels(newSessionDetails)

      existingSessionIncentiveLevels.stream().anyMatch { newSessionIncentiveLevels.contains(it) }
    } else {
      false
    }
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

  fun getPermittedSessionLocationDtos(sessionDetails: SessionDetailsDto): Set<PermittedSessionLocationDto> {
    return sessionDetails.permittedLocationGroups.stream()
      .map { it.locations }
      .flatMap(List<PermittedSessionLocationDto>::stream).collect(Collectors.toSet())
  }

  fun getPermittedPrisonerCategoryTypes(sessionDetails: SessionDetailsDto): Set<PrisonerCategoryType> {
    return sessionDetails.prisonerCategoryGroups.stream()
      .map { it.categories }
      .flatMap(List<PrisonerCategoryType>::stream).collect(Collectors.toSet())
  }

  fun getPermittedIncentiveLevels(sessionDetails: SessionDetailsDto): Set<IncentiveLevel> {
    return sessionDetails.prisonerIncentiveLevelGroups.stream()
      .map { it.incentiveLevels }
      .flatMap(List<IncentiveLevel>::stream).collect(Collectors.toSet())
  }

  fun hasLocationMatch(
    primarySessionLocations: Set<PermittedSessionLocationDto>,
    comparedSessionLocations: Set<PermittedSessionLocationDto>,
  ): Boolean {
    var hasMatch = false
    primarySessionLocations.forEach { primarySessionLocation ->
      hasMatch = multipleLocationMatcher.test(primarySessionLocation, comparedSessionLocations)
    }

    if (!hasMatch) {
      comparedSessionLocations.forEach { primarySessionLocation ->
        hasMatch = multipleLocationMatcher.test(primarySessionLocation, primarySessionLocations)
      }
    }

    return hasMatch
  }
}
