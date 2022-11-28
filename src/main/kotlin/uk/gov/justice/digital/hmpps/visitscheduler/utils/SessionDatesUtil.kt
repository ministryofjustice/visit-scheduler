package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.WEEKS
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
    var biWeeklyFirstBookableSessionDay = firstBookableSessionDay
    if (isSkipWeek(sessionTemplate.validFromDate, firstBookableSessionDay)) {
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

  private fun isSkipWeek(
    validFromDate: LocalDate,
    firstBookableSessionDay: LocalDate
  ) = WEEKS.between(validFromDate, firstBookableSessionDay.plusDays(1)).toInt() % 2 != 0
}
