package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class MigrateVisitServiceTest {

  private val legacyDataRepository = mock<LegacyDataRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val prisonConfigService = mock<PrisonConfigService>()
  private val snsService = mock<SnsService>()
  private val prisonerService = mock<PrisonerService>()
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
        capacityGroup = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.minusWeeks(1),
        startTime = LocalTime.parse("13:10:00"),
        endTime = LocalTime.parse("13:50:00"),
        capacityGroup = "failed",
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

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results).hasSize(7)
    assertThat(results[0].startTime).isEqualTo("13:10:00")
    assertThat(results[0].endTime).isEqualTo("13:50:00")
    assertThat(results[0].capacityGroup).isEqualTo("passed")
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
        capacityGroup = "failed",
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
        capacityGroup = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
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
        capacityGroup = "passed",
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
        capacityGroup = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
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
        capacityGroup = "passed",
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
        capacityGroup = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
  }

  @Test
  fun `get nearest template session for identical sessions and dont return first one (expired one)`() {
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
        capacityGroup = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        capacityGroup = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
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
        capacityGroup = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate.plusDays(1),
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        capacityGroup = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
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
        capacityGroup = "passed",
        dayOfWeek = dayOfWeek,
      ),
    )

    sessionTemplates.add(
      sessionTemplate(
        prisonCode = prisonCode,
        validFromDate = sessionDate,
        startTime = LocalTime.parse("13:20:00"),
        endTime = LocalTime.parse("13:40:00"),
        capacityGroup = "failed",
        dayOfWeek = dayOfWeek,
      ),
    )

    whenever(
      sessionTemplateRepository.findValidSessionTemplatesBy(
        prisonCode = prisonCode,
        dayOfWeek = dayOfWeek,
      ),
    ).thenReturn(sessionTemplates)

    // When
    val results = toTest.getSessionTemplatesInTimeProximityOrder(prisonCode, sessionDate, startTime, endTime)

    // Then
    assertThat(results[0].capacityGroup).isEqualTo("passed")
  }
}
