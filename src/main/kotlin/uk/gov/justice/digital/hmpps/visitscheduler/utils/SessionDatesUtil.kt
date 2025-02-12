package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.TemporalAdjusters
import java.util.stream.Stream

@Component
class SessionDatesUtil {

  fun calculateDates(
    firstBookableSessionDay: LocalDate,
    lastBookableSessionDay: LocalDate,
    sessionTemplate: SessionTemplate,
  ): Stream<LocalDate> {
    val lastBookableSessionDayAdjusted = lastBookableSessionDay.plusDays(1)

    if (lastBookableSessionDayAdjusted.isBefore(firstBookableSessionDay)) {
      // There is no sessions because the first bookable date is after
      return Stream.empty()
    }
    return firstBookableSessionDay.datesUntil(
      lastBookableSessionDayAdjusted,
      Period.ofWeeks(sessionTemplate.weeklyFrequency),
    )
  }

  fun getFirstBookableSessionDay(
    sessionTemplate: SessionTemplate,
  ): LocalDate {
    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    return getFirstBookableSessionDayAdjustForFrequency(firstBookableSessionDay, sessionTemplate)
  }

  private fun getFirstBookableSessionDayAdjustForFrequency(
    firstBookableSessionDay: LocalDate,
    sessionTemplate: SessionTemplate,
  ): LocalDate {
    if (isWeeklySkipDate(firstBookableSessionDay, sessionTemplate.validFromDate, sessionTemplate.weeklyFrequency)) {
      return firstBookableSessionDay.plusWeeks(sessionTemplate.weeklyFrequency.minus(1).toLong())
    }
    return firstBookableSessionDay
  }

  fun isActiveForDate(date: LocalDate, sessionTemplate: SessionTemplate): Boolean = isActiveForDate(date, SessionTemplateDto(sessionTemplate))

  fun isActiveForDate(date: LocalDate, sessionTemplate: SessionTemplateDto): Boolean {
    if (sessionTemplate.weeklyFrequency > 1) {
      return !isWeeklySkipDate(date, sessionTemplate.sessionDateRange.validFromDate, sessionTemplate.weeklyFrequency)
    }
    return true
  }

  private fun getValidFromMonday(validFromDate: LocalDate): LocalDate {
    // This has been added just encase someone wants the session template start date other than the start of week.
    // Therefore, this use of validFromMonday will allow the bi-weekly to still work.
    return validFromDate.with(TemporalAdjusters.previousOrSame(MONDAY))
  }

  fun isWeeklySkipDate(
    sessionDate: LocalDate,
    validFromDate: LocalDate,
    weeklyFrequency: Int,
  ): Boolean {
    val validFromMonday = getValidFromMonday(validFromDate)
    return WEEKS.between(validFromMonday, sessionDate).toInt() % weeklyFrequency != 0
  }
}
