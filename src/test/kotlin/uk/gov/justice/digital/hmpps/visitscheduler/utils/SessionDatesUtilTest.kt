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
  private val toTest: SessionDatesUtil = SessionDatesUtil()

  @Test
  fun `Create Dates from firstBookableSessionDay to lastBookableSessionDay without biWeekly`() {
    // Given
    val sessionTemplate = sessionTemplate(biWeekly = false, validFromDate = LocalDate.now(), dayOfWeek = FRIDAY)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
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
  fun `biWeeklyDates - When first bookable day is this week and last bookable day is same week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = firstBookableSessionDay

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(1)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
  }

  @Test
  fun `biWeeklyDates - When first bookable day is next week and last bookable day is same week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(1).with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = firstBookableSessionDay

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(0)
  }

  @Test
  fun `biWeeklyDates - When first bookable session day is this week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(5)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay)
    assertThat(dates[0].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
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
  fun `biWeeklyDates - When first bookable session day is next week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(1).with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(4)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay.plusWeeks(1))
    assertThat(dates[0].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(3))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(5))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(7))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `biWeeklyDates - When first bookable session day is next week and validFromDate is in the past`() {
    // Given
    val validFromDate = LocalDate.now().plusWeeks(-3).with(TemporalAdjusters.next(MONDAY))
    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(1).with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(4)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay.plusWeeks(1))
    assertThat(dates[0].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(3))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(5))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(7))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `biWeeklyDates - When first bookable session day is next week and validFromDate is in the future`() {
    // Given
    val validFromDate = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(MONDAY))

    val sessionTemplate = sessionTemplate(biWeekly = true, validFromDate = validFromDate, dayOfWeek = validFromDate.plusDays(1).dayOfWeek)

    val firstBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(1).with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
    val lastBookableSessionDay = sessionTemplate.validFromDate.plusWeeks(10)

    // When
    val dates = toTest.calculateDates(firstBookableSessionDay, lastBookableSessionDay, sessionTemplate).collect(
      Collectors.toList()
    )

    // Then
    assertThat(dates).hasSize(4)
    assertThat(dates[0]).isEqualTo(firstBookableSessionDay.plusWeeks(1))
    assertThat(dates[0].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[1]).isEqualTo(firstBookableSessionDay.plusWeeks(3))
    assertThat(dates[1].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[2]).isEqualTo(firstBookableSessionDay.plusWeeks(5))
    assertThat(dates[2].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
    assertThat(dates[3]).isEqualTo(firstBookableSessionDay.plusWeeks(7))
    assertThat(dates[3].dayOfWeek).isEqualTo(sessionTemplate.dayOfWeek)
  }

  @Test
  fun `Dont not SkipWeek on Monday on first week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val firstBookableSessionDay = validFromDate

    // When
    val skip = toTest.isSkipWeek(validFromDate, firstBookableSessionDay)

    // Then
    assertThat(skip).isFalse
  }

  @Test
  fun `Dont not SkipWeek on Sunday on first week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val firstBookableSessionDay = validFromDate.with(TemporalAdjusters.next(SUNDAY))

    // When
    val skip = toTest.isSkipWeek(validFromDate, firstBookableSessionDay)

    // Then
    assertThat(skip).isFalse
  }

  @Test
  fun `SkipWeek on Monday on second week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val firstBookableSessionDay = validFromDate.with(TemporalAdjusters.next(MONDAY))

    // When
    val skip = toTest.isSkipWeek(validFromDate, firstBookableSessionDay)

    // Then
    assertThat(skip).isTrue
  }

  @Test
  fun `SkipWeek on Sunday on second week`() {
    // Given
    val validFromDate = getStartOfWeek()
    val firstBookableSessionDay = validFromDate.plusWeeks(1).with(TemporalAdjusters.next(SUNDAY))

    // When
    val skip = toTest.isSkipWeek(validFromDate, firstBookableSessionDay)

    // Then
    assertThat(skip).isTrue
  }

  private fun getStartOfWeek() = LocalDate.now().with(TemporalAdjusters.next(MONDAY))
}
