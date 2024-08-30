package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.stream.Collectors

@ExtendWith(MockitoExtension::class)
class SessionDatesUtilTest {

  @InjectMocks
  val toTest: SessionDatesUtil = SessionDatesUtil()

  data class SkipWeekTestCase(val firstBookableSessionDay: LocalDate, val validFromDate: LocalDate, val weeklyFrequency: Int = 1) {

    fun test(): Boolean {
      val sessionTemplate = sessionTemplate(validFromDate = validFromDate, weeklyFrequency = weeklyFrequency)
      return SessionDatesUtil().isWeeklySkipDate(firstBookableSessionDay, sessionTemplate.validFromDate, sessionTemplate.weeklyFrequency)
    }
  }

  @Test
  fun `Create Dates from firstBookableSessionDay to lastBookableSessionDay with weekly frequency`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), dayOfWeek = FRIDAY)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(10)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[0].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(1))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(2))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(3))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[4]).isEqualTo(firstBookableSessionDay.plusWeeks(4))
    assertThat(dates[4].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[5]).isEqualTo(firstBookableSessionDay.plusWeeks(5))
    assertThat(dates[5].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[6]).isEqualTo(firstBookableSessionDay.plusWeeks(6))
    assertThat(dates[6].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[7]).isEqualTo(firstBookableSessionDay.plusWeeks(7))
    assertThat(dates[7].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[8]).isEqualTo(firstBookableSessionDay.plusWeeks(8))
    assertThat(dates[8].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[9]).isEqualTo(firstBookableSessionDay.plusWeeks(9))
    assertThat(dates[9].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable day is this week and last bookable day is same week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = firstBookableSessionDay

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(1)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable day is next week and last bookable day is same week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = validFromDate.with ( TemporalAdjusters.next(sessionTemplate.dayOfWeek) )
    val lastBookableSessionDay = firstBookableSessionDay

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(1)
    assertThat(dates).contains(firstBookableSessionDay)
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable session day is this week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(5)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[1]).isEqualTo(dates[0].plusWeeks(2))
    assertThat(dates[2]).isEqualTo(dates[1].plusWeeks(2))
    assertThat(dates[3]).isEqualTo(dates[2].plusWeeks(2))
    assertThat(dates[4]).isEqualTo(dates[3].plusWeeks(2))
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable session day is next week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = validFromDate.with ( TemporalAdjusters.next(sessionTemplate.dayOfWeek) )
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(5)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[1]).isEqualTo(dates[0].plusWeeks(2))
    assertThat(dates[2]).isEqualTo(dates[1].plusWeeks(2))
    assertThat(dates[3]).isEqualTo(dates[2].plusWeeks(2))
    assertThat(dates[4]).isEqualTo(dates[3].plusWeeks(2))
  }

  @Test
  fun `three weeklyFrequencyDates - When first bookable day is next week and last bookable day is same week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 3, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = validFromDate.with ( TemporalAdjusters.next(sessionTemplate.dayOfWeek) )
    val lastBookableSessionDay = firstBookableSessionDay

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(1)
    assertThat(dates).contains(firstBookableSessionDay)
  }

  @Test
  fun `three weeklyFrequencyDates - When first bookable session day is next week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(weeklyFrequency = 3, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(1).with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(3)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[1]).isEqualTo(dates[0].plusWeeks(3))
    assertThat(dates[2]).isEqualTo(dates[1].plusWeeks(3))
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable session day is next week and validFromDate is in the past`() {
    // Given
    val validFromDate = LocalDate.now().plusWeeks(-3).with(TemporalAdjusters.next(MONDAY))
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = validFromDate.with ( TemporalAdjusters.next(sessionTemplate.dayOfWeek) )
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(5)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(2))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(4))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(6))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[4]).isEqualTo(firstBookableSessionDay.plusWeeks(8))
    assertThat(dates[4].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `bi weeklyFrequencyDates - When first bookable session day is next week and validFromDate is in the future`() {
    // Given
    val validFromDate = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(MONDAY))

    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = validFromDate.with ( TemporalAdjusters.next(sessionTemplate.dayOfWeek) )
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList(),
    )

    // Then
    assertThat(dates).hasSize(5)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(2))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(4))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(6))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[4]).isEqualTo(firstBookableSessionDay.plusWeeks(8))
    assertThat(dates[4].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 1`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 1

    val testCases = listOf<SkipWeekTestCase>(
      SkipWeekTestCase(validFromDate, validFromDate, weeklyFrequency),
      SkipWeekTestCase(validFromDate.with(TemporalAdjusters.next(SUNDAY)), validFromDate, weeklyFrequency),
      SkipWeekTestCase(validFromDate.with(TemporalAdjusters.next(MONDAY)), validFromDate, weeklyFrequency),
      SkipWeekTestCase(validFromDate.plusWeeks(1).with(TemporalAdjusters.next(SUNDAY)), validFromDate, weeklyFrequency),
    )

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isFalse
    assertThat(results[2]).isFalse
    assertThat(results[3]).isFalse
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 2`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 2

    val testCases = mutableListOf<SkipWeekTestCase>()
    for (i in 0..weeklyFrequency + 1) {
      testCases.add(SkipWeekTestCase(validFromDate.plusWeeks(i.toLong()), validFromDate, weeklyFrequency))
    }

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isTrue
    assertThat(results[2]).isFalse
    assertThat(results[3]).isTrue
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 3`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 3

    val testCases = mutableListOf<SkipWeekTestCase>()
    for (i in 0..weeklyFrequency + 1) {
      testCases.add(SkipWeekTestCase(validFromDate.plusWeeks(i.toLong()), validFromDate, weeklyFrequency))
    }

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isTrue
    assertThat(results[2]).isTrue
    assertThat(results[3]).isFalse
    assertThat(results[4]).isTrue
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 4`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 4
    val testCases = mutableListOf<SkipWeekTestCase>()
    for (i in 0..weeklyFrequency + 1) {
      testCases.add(SkipWeekTestCase(validFromDate.plusWeeks(i.toLong()), validFromDate, weeklyFrequency))
    }

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isTrue
    assertThat(results[2]).isTrue
    assertThat(results[3]).isTrue
    assertThat(results[4]).isFalse
    assertThat(results[5]).isTrue
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 5`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 5
    val testCases = mutableListOf<SkipWeekTestCase>()
    for (i in 0..weeklyFrequency + 1) {
      testCases.add(SkipWeekTestCase(validFromDate.plusWeeks(i.toLong()), validFromDate, weeklyFrequency))
    }

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isTrue
    assertThat(results[2]).isTrue
    assertThat(results[3]).isTrue
    assertThat(results[4]).isTrue
    assertThat(results[5]).isFalse
    assertThat(results[6]).isTrue
  }

  @Test
  fun `SkipWeek test - weeklyFrequency = 6`() {
    // Given
    val validFromDate = getStartOfWeek()
    val weeklyFrequency = 6
    val testCases = mutableListOf<SkipWeekTestCase>()
    for (i in 0..weeklyFrequency + 1) {
      testCases.add(SkipWeekTestCase(validFromDate.plusWeeks(i.toLong()), validFromDate, weeklyFrequency))
    }

    // When
    val results = testCases.map { it.test() }
    // Then
    assertThat(results[0]).isFalse
    assertThat(results[1]).isTrue
    assertThat(results[2]).isTrue
    assertThat(results[3]).isTrue
    assertThat(results[4]).isTrue
    assertThat(results[5]).isTrue
    assertThat(results[6]).isFalse
    assertThat(results[7]).isTrue
  }

  private fun getStartOfWeek() = LocalDate.now().with(TemporalAdjusters.next(MONDAY))
}
