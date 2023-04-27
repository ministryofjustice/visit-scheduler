package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.exception.MatchSessionTemplateToMigratedVisitException
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class MigrateVisitServiceProximityTest {

  private val legacyDataRepository = mock<LegacyDataRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val prisonConfigService = mock<PrisonConfigService>()
  private val snsService = mock<SnsService>()
  private val sessionTemplates = mock<SessionService>()
  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val authenticationHelperService = mock<AuthenticationHelperService>()
  private val telemetryClient = mock<TelemetryClient>()

  @InjectMocks
  private lateinit var toTest: MigrateVisitService

  @Test
  fun `get nearest template session for given date`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now()

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("08:30:00"),
        endTime = LocalTime.parse("21:30:00"),
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("09:30:00"),
        endTime = LocalTime.parse("15:30:00"),
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("18:30:00"),
        endTime = LocalTime.parse("21:30:00"),
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:10:00"),
        endTime = LocalTime.parse("13:50:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.minusWeeks(1),
        startTime = LocalTime.parse("13:10:00"),
        endTime = LocalTime.parse("13:50:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("10:30:00"),
        endTime = LocalTime.parse("11:30:00"),
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results).hasSize(3)
    assertThat(results[0].startTime).isEqualTo("13:10:00")
    assertThat(results[0].endTime).isEqualTo("13:50:00")
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical expired sessions and return last expired (last one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(5),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(6),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical expired sessions and return last expired (first one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(6),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(5),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical sessions and dont return last one (expired one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(5),
        startTime = sessionTemplates[0].startTime,
        endTime = sessionTemplates[0].endTime,
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical sessions and dont return first one (expired one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().plusDays(1)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        validToDate = sessionDate.plusDays(5),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical sessions with no valid to date (last one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical sessions with no valid to date (first one)`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates)

    // When
    val results =
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, "youWontFindMe")

    // Then
    assertThat(results[0].visitRoom).isEqualTo("passed")
  }

  @Test
  fun `when template with capacity same as room name call repo with capacity`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek
    val visitRoom = "youWillFindMe"

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates, visitRoom)

    // When
    toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, visitRoom)

    // Then
    verify(sessionTemplateRepository, times(1)).findValidSessionTemplatesBy(
      rangeStartDate = LocalDate.now(),
      prisonCode = prisonCode,
      dayOfWeek = dayOfWeek,
      visitRoom = visitRoom,
    )
    verify(sessionTemplateRepository, times(0)).findValidSessionTemplatesBy(
      rangeStartDate = LocalDate.now(),
      prisonCode = prisonCode,
      dayOfWeek = dayOfWeek,
    )
  }

  @Test
  fun `When no sessionTemplate are found exception is throw`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek
    val visitRoom = "youWillFindMe"

    val sessionTemplates = mutableListOf<SessionTemplate>()

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates, visitRoom)

    // When
    val exception = Assertions.assertThrows(MatchSessionTemplateToMigratedVisitException::class.java) {
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, visitRoom)
    }

    // Then
    assertThat(exception).hasMessageStartingWith("Could not find any SessionTemplate for future visit date ")
  }

  @Test
  fun `When one sessionTemplate is found but is greater than max proximity an exception is throw`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek
    val visitRoom = "youWillFindMe"

    val sessionTemplates = mutableListOf<SessionTemplate>()
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.plusMinutes(60),
        endTime = endTime.plusMinutes(121),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )
    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates, visitRoom)

    // When
    val exception = Assertions.assertThrows(MatchSessionTemplateToMigratedVisitException::class.java) {
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, visitRoom)
    }

    // Then
    assertThat(exception).hasMessageStartingWith("Could not find suitable SessionTemplate within max proximity of $DEFAULT_MAX_PROX_MINUTES for future visit date")
  }

  @Test
  fun `When sessionTemplates are found but all are greater than max proximity an exception is throw`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek
    val visitRoom = "youWillFindMe"

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime,
        endTime = endTime.plusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.minusMinutes(181),
        endTime = endTime.plusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.minusMinutes(181),
        endTime = endTime.minusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )

    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates, visitRoom)

    // When
    val exception = Assertions.assertThrows(MatchSessionTemplateToMigratedVisitException::class.java) {
      toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, visitRoom)
    }

    // Then
    assertThat(exception).hasMessageStartingWith("Could not find suitable SessionTemplate within max proximity of $DEFAULT_MAX_PROX_MINUTES for future visit date")
  }

  @Test
  fun `When most sessionTemplates have greater than max proximity but one is below no exception is throw and session is returned`() {
    // Given
    val prisonCode = "BMI"
    val sessionDateStart = LocalDateTime.now().minusDays(10)

    val sessionDate = sessionDateStart.toLocalDate()
    val startTime = LocalTime.parse("13:00:00")
    val endTime = LocalTime.parse("14:00:00")
    val dayOfWeek = sessionDateStart.dayOfWeek
    val visitRoom = "youWillFindMe"

    val sessionTemplates = mutableListOf<SessionTemplate>()

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime,
        endTime = endTime.plusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.minusMinutes(181),
        endTime = endTime.plusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )
    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.minusMinutes(181),
        endTime = endTime.minusMinutes(181),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = startTime.minusMinutes(10),
        endTime = endTime.minusMinutes(10),
        visitRoom = visitRoom,
        dayOfWeek = dayOfWeek,
      ),
    )
    mockSessionTemplateRepo(prisonCode, dayOfWeek, sessionTemplates, visitRoom)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime, visitRoom)

    // Then
    assertThat(results).hasSize(1)
  }

  private fun mockSessionTemplateRepo(
    prisonCode: String,
    dayOfWeek: DayOfWeek?,
    sessionTemplates: MutableList<SessionTemplate>,
    visitRoom: String? = null,
  ) {
    val rangeStartDate = LocalDate.now()

    if (visitRoom != null) {
      whenever(
        sessionTemplateRepository.findValidSessionTemplatesBy(
          rangeStartDate = rangeStartDate,
          prisonCode = prisonCode,
          dayOfWeek = dayOfWeek,
          visitRoom = visitRoom,
        ),
      ).thenReturn(sessionTemplates.filter { it.visitRoom == visitRoom })
    } else {
      whenever(
        sessionTemplateRepository.findValidSessionTemplatesBy(
          rangeStartDate = rangeStartDate,
          prisonCode = prisonCode,
          dayOfWeek = dayOfWeek,
        ),
      ).thenReturn(sessionTemplates)

      whenever(
        sessionTemplateRepository.findValidSessionTemplatesBy(
          rangeStartDate = rangeStartDate,
          prisonCode = prisonCode,
          dayOfWeek = dayOfWeek,
          visitRoom = "youWontFindMe",
        ),
      ).thenReturn(listOf())
    }
  }
}
