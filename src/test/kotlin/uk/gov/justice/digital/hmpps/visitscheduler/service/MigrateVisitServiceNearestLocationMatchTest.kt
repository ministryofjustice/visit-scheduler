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
import uk.gov.justice.digital.hmpps.visitscheduler.integration.migration.createMigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.PermittedSessionLocation
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.location.SessionLocationGroup
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalTime

@ExtendWith(MockitoExtension::class)
class MigrateVisitServiceNearestLocationMatchTest {

  private val legacyDataRepository = mock<LegacyDataRepository>()
  private val visitRepository = mock<VisitRepository>()
  private val prisonConfigService = mock<PrisonConfigService>()
  private val snsService = mock<SnsService>()
  private val sessionService = mock<SessionService>()
  private val sessionTemplateRepository = mock<SessionTemplateRepository>()
  private val authenticationHelperService = mock<AuthenticationHelperService>()
  private val telemetryClient = mock<TelemetryClient>()

  @InjectMocks
  private lateinit var toTest: MigrateVisitService

  @Test
  fun `get nearest template session for prisoner location`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()

    val prisonerId = migrateVisitRequestDto.prisonerId
    val prisonCode = migrateVisitRequestDto.prisonCode
    val sessionDateStart = migrateVisitRequestDto.startTimestamp
    val sessionDate = sessionDateStart.toLocalDate()
    val dayOfWeek = sessionDateStart.dayOfWeek
    val prison = Prison(id = 1, prisonCode, true)

    val group1 = SessionLocationGroup(prison.id, "group1", prison)
    group1.sessionLocations.add(PermittedSessionLocation(group1.id, group1, "1"))
    group1.sessionLocations.add(PermittedSessionLocation(group1.id, group1, "1", "2"))

    val group2 = SessionLocationGroup(prison.id, "group2", prison)
    group2.sessionLocations.add(PermittedSessionLocation(group1.id, group1, "1", "2", "3"))

    val permittedSessionLocationGroups1 = mutableListOf<SessionLocationGroup>()
    permittedSessionLocationGroups1.add(group1)
    permittedSessionLocationGroups1.add(group2)

    val sessionTemplate1 = sessionTemplate(
      name = "fail1_",
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      startTime = LocalTime.parse("08:30:00"),
      endTime = LocalTime.parse("21:30:00"),
      dayOfWeek = dayOfWeek,
      permittedSessionLocationGroups = permittedSessionLocationGroups1,
    )

    val group12 = SessionLocationGroup(prison.id, "group12", prison)
    group12.sessionLocations.add(PermittedSessionLocation(group12.id, group12, "1", "2", "3", "4"))

    val permittedSessionLocationGroups2 = mutableListOf<SessionLocationGroup>()
    permittedSessionLocationGroups2.add(group12)

    val sessionTemplate2 = sessionTemplate(
      name = "pass_",
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      startTime = LocalTime.parse("08:30:00"),
      endTime = LocalTime.parse("21:30:00"),
      dayOfWeek = dayOfWeek,
      permittedSessionLocationGroups = permittedSessionLocationGroups2,
    )

    val sessionTemplate3 = sessionTemplate(
      name = "fail2_",
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      startTime = LocalTime.parse("08:30:00"),
      endTime = LocalTime.parse("21:30:00"),
      dayOfWeek = dayOfWeek,
    )

    val sessionTemplates = mutableListOf<SessionTemplate>()
    sessionTemplates.add(sessionTemplate3)
    sessionTemplates.add(sessionTemplate2)
    sessionTemplates.add(sessionTemplate1)

    mockSessionFilterSessionTemplatesForLocation(sessionTemplates, prisonerId, prisonCode)

    // When
    val results =
      toTest.getNearestTemplateThatMatchesPrisonerLocation(migrateVisitRequestDto, sessionTemplates)

    // Then
    assertThat(results.name).isEqualTo("pass_" + dayOfWeek.name)
  }

  @Test
  fun `get first template session when no matching sessions by prisoner location`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()

    val prisonerId = migrateVisitRequestDto.prisonerId
    val prisonCode = migrateVisitRequestDto.prisonCode
    val sessionDateStart = migrateVisitRequestDto.startTimestamp
    val sessionDate = sessionDateStart.toLocalDate()
    val dayOfWeek = sessionDateStart.dayOfWeek

    val sessionTemplate1 = sessionTemplate(
      name = "pass_",
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      startTime = LocalTime.parse("08:30:00"),
      endTime = LocalTime.parse("21:30:00"),
      dayOfWeek = dayOfWeek,
    )

    val sessionTemplate2 = sessionTemplate(
      name = "fail_",
      prisonCode = prisonCode,
      validFromDate = sessionDate,
      startTime = LocalTime.parse("08:30:00"),
      endTime = LocalTime.parse("21:30:00"),
      dayOfWeek = dayOfWeek,
    )

    val sessionTemplates = mutableListOf<SessionTemplate>()
    sessionTemplates.add(sessionTemplate1)
    sessionTemplates.add(sessionTemplate2)

    mockSessionFilterSessionTemplatesForLocation(mutableListOf(), prisonerId, prisonCode)

    // When
    val results =
      toTest.getNearestTemplateThatMatchesPrisonerLocation(migrateVisitRequestDto, sessionTemplates)

    // Then
    assertThat(results.name).isEqualTo("pass_" + dayOfWeek.name)
  }

  private fun mockSessionFilterSessionTemplatesForLocation(
    sessionTemplates: MutableList<SessionTemplate>,
    prisonerId: String,
    prisonCode: String,
  ) {
    val rangeStartDate = LocalDate.now()

    whenever(
      sessionService.filterSessionsTemplatesForLocation(
        sessionTemplates = sessionTemplates,
        prisonerId = prisonerId,
        prisonCode = prisonCode,
        mustHaveLocationGroups = true,
      ),
    ).thenReturn(sessionTemplates)
  }
}
