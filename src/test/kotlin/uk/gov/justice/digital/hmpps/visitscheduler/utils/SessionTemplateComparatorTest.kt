package uk.gov.justice.digital.hmpps.visitscheduler.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.AllowedSessionLocationHierarchy
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createCreateSessionTemplateDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createSessionLocationGroupDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getSessionDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionCategoryMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionIncentiveLevelMatcher
import uk.gov.justice.digital.hmpps.visitscheduler.utils.matchers.SessionLocationMatcher
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class SessionTemplateComparatorTest {

  @InjectMocks
  val toTest: SessionTemplateComparator = SessionTemplateComparator(SessionDatesUtil(), SessionLocationMatcher(), SessionCategoryMatcher(), SessionIncentiveLevelMatcher(), SessionTemplateUtil())

  @Test
  fun `when new session template has same details as existing session template and hence it overlaps`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has different day of week than existing session template then overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      dayOfWeek = DayOfWeek.MONDAY,
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template has valid from date between existing valid dates overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate.plusDays(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has valid from date before existing valid from date and valid to date before existing valid to date overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate.minusDays(1), validToDate = sessionTemplate.validFromDate.plusDays(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has valid from date before existing and valid to date as null overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate.minusDays(1), validToDate = null),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has valid from date before existing from and valid to date after existing to date overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate.minusDays(1), validToDate = sessionTemplate.validToDate!!.plusDays(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template starts and ends before existing session template overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validFromDate.minusDays(15), validToDate = sessionTemplate.validFromDate.minusDays(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template starts and ends after existing session template overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(validFromDate = sessionTemplate.validToDate!!.plusDays(1), validToDate = sessionTemplate.validToDate!!.plusDays(21)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template has session start time between existing session times overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), validFromDate = LocalDate.now(), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session time slot is different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime.plusMinutes(1), endTime = sessionTemplate.endTime.plusMinutes(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has session start time before existing session start time and session end time before existing session end time overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session time slot is different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime.minusMinutes(1), endTime = sessionTemplate.endTime.minusMinutes(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template has session start time before existing session start and session end time after existing end time overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session time slot is different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime.minusMinutes(1), endTime = sessionTemplate.endTime.plusMinutes(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new session template starts and end times are before existing session template overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session time slot is different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.startTime.minusMinutes(15), endTime = sessionTemplate.startTime.minusMinutes(1)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template starts and end times are after existing session template overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1), dayOfWeek = DayOfWeek.FRIDAY)
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session time slot is different
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionTimeSlot = SessionTimeSlotDto(startTime = sessionTemplate.endTime.plusMinutes(1), endTime = sessionTemplate.endTime.plusMinutes(15)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template has different weekly frequency than existing overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only weeklyFrequency is different
    // since the weekly frequency is different sessions will overlap after n weeks
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      weeklyFrequency = sessionTemplate.weeklyFrequency + 1,
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new and old session templates have weekly frequency of 1 than existing overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only weeklyFrequency is different
    // since the weekly frequency is different sessions will overlap after n weeks
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      weeklyFrequency = 1,
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new and old session templates have weekly frequency of 2 but they are overlapping weeks then overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template except for validFromDate
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusWeeks(12)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new and old session templates have weekly frequency of 2 but they are not overlapping weeks then overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 2, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template except for validFromDate
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusWeeks(13)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new and old session templates have weekly frequency of 7 but they are overlapping weeks then overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 7, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template except for validFromDate
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusWeeks(35)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }

  @Test
  fun `when new and old session templates have weekly frequency of 7 but they are not overlapping weeks then overlap is false`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 7, validFromDate = LocalDate.now(), validToDate = LocalDate.now().plusYears(1))
    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template except for validFromDate
    val newSessionTemplate = createCreateSessionTemplateDto(
      sessionTemplateDto = existingSessionTemplateDto,
      sessionDateRange = SessionDateRangeDto(LocalDate.now().plusWeeks(13)),
    )

    // When
    val hasOverlap = toTest.hasOverlap(getSessionDetailsDto(newSessionTemplate), SessionDetailsDto(existingSessionTemplateDto))

    // Then
    Assertions.assertThat(hasOverlap).isFalse
  }

  @Test
  fun `when new session template has 1 common location as existing session template overlap is true`() {
    // Given
    val sessionTemplate = sessionTemplate(weeklyFrequency = 1, validFromDate = LocalDate.now(), dayOfWeek = DayOfWeek.FRIDAY)
    val allowedSessionLocations = listOf(AllowedSessionLocationHierarchy("A", "1", "001"))

    val sessionLocationGroupDto = createSessionLocationGroupDto(allowedSessionLocations)

    val existingSessionTemplateDto = SessionTemplateDto(sessionTemplate)

    // new session template has rest of the details same as the existing session template, only session dates are different
    val newSessionTemplateDetails = getSessionDetailsDto(
      createCreateSessionTemplateDto(sessionTemplateDto = existingSessionTemplateDto),
      permittedLocationGroups = listOf(sessionLocationGroupDto),
    )
    // When
    val hasOverlap = toTest.hasOverlap(newSessionTemplateDetails, SessionDetailsDto(existingSessionTemplateDto))
    // Then
    Assertions.assertThat(hasOverlap).isTrue
  }
}
