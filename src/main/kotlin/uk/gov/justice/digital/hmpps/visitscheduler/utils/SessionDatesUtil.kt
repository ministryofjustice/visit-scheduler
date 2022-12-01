package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
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
    sessionTemplate: SessionTemplate
  ): Stream<LocalDate> {

    if (sessionTemplate.biWeekly) {
      return biWeeklyDates(firstBookableSessionDay, sessionTemplate, lastBookableSessionDay)
    }

    return firstBookableSessionDay.datesUntil(lastBookableSessionDay.plusDays(1), Period.ofWeeks(1))
  }

  private fun biWeeklyDates(
    firstBookableSessionDay: LocalDate,
    sessionTemplate: SessionTemplate,
    lastBookableSessionDay: LocalDate
  ): Stream<LocalDate> {
    // This has been added just encase some one wants the session template start date other than the start of week.
    // Therefore, this use of validFromMonday will allow the bi-weekly to still work.
    val validFromMonday = getValidFromMonday(sessionTemplate)

    var biWeeklyFirstBookableSessionDay = firstBookableSessionDay
    if (isSkipWeek(validFromMonday, firstBookableSessionDay)) {
      biWeeklyFirstBookableSessionDay = firstBookableSessionDay.plusWeeks(1)
    }
    val adjustedLastBookableSessionDay = lastBookableSessionDay.plusDays(1)
    if (adjustedLastBookableSessionDay < biWeeklyFirstBookableSessionDay) {
      // This is to prevent a IllegalArgumentException with datesUntil call
      return Stream.empty()
    }
    return biWeeklyFirstBookableSessionDay.datesUntil(
      adjustedLastBookableSessionDay, Period.ofWeeks(2)
    )
  }

  private fun getValidFromMonday(sessionTemplate: SessionTemplate): LocalDate {
    if (sessionTemplate.validFromDate.dayOfWeek != MONDAY) {
      return sessionTemplate.validFromDate.with(TemporalAdjusters.previous(MONDAY))
    }
    return sessionTemplate.validFromDate
  }

  fun isSkipWeek(
    validFromDate: LocalDate,
    firstBookableSessionDay: LocalDate
  ) = WEEKS.between(validFromDate, firstBookableSessionDay).toInt() % 2 != 0
}
