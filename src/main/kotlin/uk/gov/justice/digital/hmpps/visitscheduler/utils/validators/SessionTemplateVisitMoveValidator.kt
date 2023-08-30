package uk.gov.justice.digital.hmpps.visitscheduler.utils.validators

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateVisitStatsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.exception.VSiPValidationException
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionDatesUtil
import uk.gov.justice.digital.hmpps.visitscheduler.utils.SessionTemplateUtil
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionCategoryMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionIncentiveLevelMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher
import java.time.LocalDate
import java.util.*

@Component
class SessionTemplateVisitMoveValidator(
  private val sessionTemplateUtil: SessionTemplateUtil,
  private val sessionLocationMatcher: SessionLocationMatcher,
  private val sessionCategoryMatcher: SessionCategoryMatcher,
  private val sessionIncentiveLevelMatcher: SessionIncentiveLevelMatcher,
  private val sessionDatesUtil: SessionDatesUtil,
) {
  private final val allowedStartTimeRangeInMinutes: Long = 60

  enum class ErrorScenario {
    PRISON_MISMATCH,
    DAY_OF_WEEK_MISMATCH,
    SESSION_TIME_MISMATCH,
    WEEKLY_FREQUENCY_MISMATCH,
    SESSION_DATES_NOT_VALID,
    LOCATION_MISMATCH,
    CATEGORY_MISMATCH,
    INCENTIVE_LEVEL_MISMATCH,
  }

  private fun getErrorMessage(
    errorScenario: ErrorScenario,
  ): String {
    return when (errorScenario) {
      ErrorScenario.PRISON_MISMATCH -> "From and to session templates have different prison codes"
      ErrorScenario.DAY_OF_WEEK_MISMATCH -> "From and to session templates have different day of week"
      ErrorScenario.SESSION_TIME_MISMATCH -> "New session time is out of the current allowed range of $allowedStartTimeRangeInMinutes minutes for a visit move"
      ErrorScenario.WEEKLY_FREQUENCY_MISMATCH -> "New weekly frequency might not accommodate all migrated visits."
      ErrorScenario.SESSION_DATES_NOT_VALID -> "New session template dates cannot accommodate all migrated visits."
      ErrorScenario.LOCATION_MISMATCH -> "New session template locations cannot accommodate all locations in existing session template."
      ErrorScenario.CATEGORY_MISMATCH -> "New session template categories cannot accommodate all categories in existing session template."
      ErrorScenario.INCENTIVE_LEVEL_MISMATCH -> "New session template incentive levels cannot accommodate all incentive levels in existing session template."
    }
  }

  private fun validateVisitMove(
    fromSessionTemplate: SessionTemplateDto,
    fromSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    toSessionTemplate: SessionTemplateDto,
    toSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    fromDate: LocalDate,
  ): List<String> {
    val errorMessages = mutableListOf<String>()
    if (fromSessionTemplate.prisonCode != toSessionTemplate.prisonCode) {
      errorMessages.add(getErrorMessage(ErrorScenario.PRISON_MISMATCH))
    }
    if (fromSessionTemplate.dayOfWeek != toSessionTemplate.dayOfWeek) {
      errorMessages.add(getErrorMessage(ErrorScenario.DAY_OF_WEEK_MISMATCH))
    }
    if (fromSessionTemplate.sessionTimeSlot != toSessionTemplate.sessionTimeSlot) {
      if (!isTimeChangeWithinRange(fromSessionTemplate.sessionTimeSlot, toSessionTemplate.sessionTimeSlot)) {
        errorMessages.add(getErrorMessage(ErrorScenario.SESSION_TIME_MISMATCH))
      }
    }

    val allVisitDates = getVisitDatesAfterDate(fromSessionTemplateVisitStats, fromDate)

    if (allVisitDates.isNotEmpty()) {
      if (!isValidSessionDate(allVisitDates, toSessionTemplate)) {
        errorMessages.add(getErrorMessage(ErrorScenario.SESSION_DATES_NOT_VALID))
      }

      if (!isValidWeeklyFrequency(fromSessionTemplate, toSessionTemplate, allVisitDates)) {
        errorMessages.add(getErrorMessage(ErrorScenario.WEEKLY_FREQUENCY_MISMATCH))
      }
    }

    if (!hasAllFromLocationsMatch(fromSessionTemplate, toSessionTemplate)) {
      errorMessages.add(getErrorMessage(ErrorScenario.LOCATION_MISMATCH))
    }

    if (!hasAllCategoriesMatch(fromSessionTemplate, toSessionTemplate)) {
      errorMessages.add(getErrorMessage(ErrorScenario.CATEGORY_MISMATCH))
    }

    if (!hasAllIncentiveLevelsMatch(fromSessionTemplate, toSessionTemplate)) {
      errorMessages.add(getErrorMessage(ErrorScenario.INCENTIVE_LEVEL_MISMATCH))
    }

    val exceededCapacityDates = doesSessionExceedCapacity(fromSessionTemplateVisitStats, toSessionTemplateVisitStats, toSessionTemplate.sessionCapacity)
    exceededCapacityDates.forEach {
      errorMessages.add("Session capacity for $it will exceed allowed session capacity post migration.")
    }
    return errorMessages
  }

  private fun isTimeChangeWithinRange(fromSessionTimeSlot: SessionTimeSlotDto, toSessionTimeSlot: SessionTimeSlotDto): Boolean {
    val from = fromSessionTimeSlot.startTime.minusMinutes(allowedStartTimeRangeInMinutes)
    val to = fromSessionTimeSlot.startTime.plusMinutes(allowedStartTimeRangeInMinutes)
    return toSessionTimeSlot.startTime in from..to
  }

  private fun isDateCoveredInSession(date: LocalDate, toSessionTemplate: SessionTemplateDto): Boolean {
    val toSessionFromDate = toSessionTemplate.sessionDateRange.validFromDate
    var toSessionToDate = toSessionTemplate.sessionDateRange.validToDate
    if (toSessionToDate == null) {
      toSessionToDate = if (date.isBefore(toSessionFromDate)) toSessionFromDate else date
    }

    return date in (toSessionFromDate..toSessionToDate)
  }

  private fun hasAllFromLocationsMatch(fromSessionTemplate: SessionTemplateDto, toSessionTemplate: SessionTemplateDto): Boolean {
    val fromSessionLocations =
      sessionTemplateUtil.getPermittedSessionLocations(fromSessionTemplate.permittedLocationGroups)
    val toSessionLocations =
      sessionTemplateUtil.getPermittedSessionLocations(toSessionTemplate.permittedLocationGroups)

    return sessionLocationMatcher.hasAllLowerOrEqualMatch(fromSessionLocations, toSessionLocations)
  }

  private fun hasAllCategoriesMatch(fromSessionTemplate: SessionTemplateDto, toSessionTemplate: SessionTemplateDto): Boolean {
    val fromSessionCategories =
      sessionTemplateUtil.getPermittedPrisonerCategoryTypes(fromSessionTemplate.prisonerCategoryGroups)
    val toSessionCategories =
      sessionTemplateUtil.getPermittedPrisonerCategoryTypes(toSessionTemplate.prisonerCategoryGroups)

    return sessionCategoryMatcher.hasAllMatch(fromSessionCategories, toSessionCategories)
  }

  private fun hasAllIncentiveLevelsMatch(fromSessionTemplate: SessionTemplateDto, toSessionTemplate: SessionTemplateDto): Boolean {
    val fromSessionIncentiveLevels =
      sessionTemplateUtil.getPermittedIncentiveLevels(fromSessionTemplate.prisonerIncentiveLevelGroups)
    val toSessionIncentiveLevels =
      sessionTemplateUtil.getPermittedIncentiveLevels(toSessionTemplate.prisonerIncentiveLevelGroups)

    return sessionIncentiveLevelMatcher.hasAllMatch(fromSessionIncentiveLevels, toSessionIncentiveLevels)
  }

  private fun isValidSessionDate(allVisitDates: Set<LocalDate>, toSessionTemplate: SessionTemplateDto): Boolean {
    return if (allVisitDates.isNotEmpty()) {
      val firstVisitDate = allVisitDates.stream().min(Comparator.naturalOrder()).get()
      val lastVisitDate = allVisitDates.stream().max(Comparator.naturalOrder()).get()

      (isDateCoveredInSession(firstVisitDate, toSessionTemplate) && isDateCoveredInSession(lastVisitDate, toSessionTemplate))
    } else {
      true
    }
  }

  private fun isValidWeeklyFrequency(fromSessionTemplate: SessionTemplateDto, toSessionTemplate: SessionTemplateDto, allVisitDates: Set<LocalDate>): Boolean {
    // check if all existing visits can be accommodated in the new session template
    if (fromSessionTemplate.weeklyFrequency % toSessionTemplate.weeklyFrequency != 0) {
      allVisitDates.forEach {
        if (!sessionDatesUtil.isActiveForDate(it, toSessionTemplate)) {
          return false
        }
      }
    }

    return true
  }

  private fun doesSessionExceedCapacity(
    fromSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    toSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    allowedToSessionCapacity: SessionCapacityDto,
  ): List<LocalDate> {
    val exceededSessionCapacityDates = mutableListOf<LocalDate>()
    val toSessionVisits = toSessionTemplateVisitStats.visitsByDate?.associateBy { it.visitDate }
    if (toSessionVisits != null) {
      fromSessionTemplateVisitStats.visitsByDate?.forEach {
        val toSessionTemplateVisitsForDate = toSessionVisits[it.visitDate]
        if (toSessionTemplateVisitsForDate != null) {
          val totalCapacityForVisitDate = it.visitCounts + toSessionTemplateVisitsForDate.visitCounts
          if (sessionTemplateUtil.isCapacityExceeded(allowedToSessionCapacity, totalCapacityForVisitDate)) {
            exceededSessionCapacityDates.add(it.visitDate)
          }
        }
      }
    }

    return exceededSessionCapacityDates.toList()
  }

  fun getVisitDatesAfterDate(visitStats: SessionTemplateVisitStatsDto, fromDate: LocalDate): Set<LocalDate> {
    return visitStats.visitsByDate?.stream()
      ?.filter { it.visitDate.isEqual(fromDate) || it.visitDate.isAfter(fromDate) }
      ?.map { it.visitDate }?.toList()?.toSet() ?: emptySet()
  }

  @Throws(VSiPValidationException::class)
  fun validateMoveSessionTemplateVisits(
    fromSessionTemplate: SessionTemplateDto,
    fromSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    toSessionTemplate: SessionTemplateDto,
    toSessionTemplateVisitStats: SessionTemplateVisitStatsDto,
    fromDate: LocalDate,
  ) {
    val errorMessages = validateVisitMove(fromSessionTemplate, fromSessionTemplateVisitStats, toSessionTemplate, toSessionTemplateVisitStats, fromDate)
    if (errorMessages.isNotEmpty()) {
      throw VSiPValidationException(errorMessages.toTypedArray())
    }
  }
}
