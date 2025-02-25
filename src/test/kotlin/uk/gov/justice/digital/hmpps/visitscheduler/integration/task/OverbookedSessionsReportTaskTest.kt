package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.OverbookedSessionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.service.reporting.VisitsReportingService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Transactional(propagation = Propagation.SUPPORTS)
@DisplayName("Overbooked Sessions Reporting")
class OverbookedSessionsReportTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var reportingService: VisitsReportingService

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  lateinit var prison1: Prison

  lateinit var sessionTemplate1: SessionTemplate
  lateinit var sessionTemplate2: SessionTemplate
  lateinit var sessionTemplate3: SessionTemplate
  lateinit var sessionTemplate4: SessionTemplate
  lateinit var sessionTemplate5: SessionTemplate
  lateinit var sessionTemplate6: SessionTemplate
  lateinit var sessionTemplate7: SessionTemplate

  private fun getSessionTemplate(dayOfWeek: DayOfWeek): SessionTemplate = sessionTemplateEntityHelper.create(
    prison = prison1,
    validFromDate = LocalDate.now().minusDays(28).with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
    validToDate = null,
    dayOfWeek = dayOfWeek,
    openCapacity = 1,
    closedCapacity = 1,
  )

  private fun createVisit(sessionTemplate: SessionTemplate, slotDate: LocalDate? = null, visitRestriction: VisitRestriction = OPEN) {
    visitEntityHelper.create(
      prisonCode = prison1.code,
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      slotDate = slotDate ?: getNextSlotDate(sessionTemplate),
      visitRestriction = visitRestriction,
    )
  }

  private fun getNextSlotDate(sessionTemplate: SessionTemplate): LocalDate = if (LocalDate.now().dayOfWeek == sessionTemplate.dayOfWeek) {
    LocalDate.now()
  } else {
    LocalDate.now().with(TemporalAdjusters.next(sessionTemplate.dayOfWeek))
  }

  @BeforeEach
  fun setupData() {
    deleteEntityHelper.deleteAll()

    // active prison with multiple sessions
    prison1 = prisonEntityHelper.create("ABC", activePrison = true, excludeDates = emptyList())
    //
    sessionTemplate1 = getSessionTemplate(dayOfWeek = DayOfWeek.MONDAY)
    // future session
    sessionTemplate2 = getSessionTemplate(dayOfWeek = DayOfWeek.TUESDAY)
    // valid session
    sessionTemplate3 = getSessionTemplate(dayOfWeek = DayOfWeek.WEDNESDAY)
    // valid session but not for same report date
    sessionTemplate4 = getSessionTemplate(dayOfWeek = DayOfWeek.THURSDAY)
    // valid session but not same weekly frequency
    sessionTemplate5 = getSessionTemplate(dayOfWeek = DayOfWeek.FRIDAY)
    // valid session with visits
    sessionTemplate6 = getSessionTemplate(dayOfWeek = DayOfWeek.SATURDAY)
    // valid session with visits
    sessionTemplate7 = getSessionTemplate(dayOfWeek = DayOfWeek.SUNDAY)
  }

  @Test
  fun `when overbooked sessions report is called all overbooked sessions are returned`() {
    // Given
    val slotDateSession1 = getNextSlotDate(sessionTemplate1)
    val slotDateSession2 = getNextSlotDate(sessionTemplate2)

    // 2 OPEN visits exist for sessionTemplate1
    createVisit(sessionTemplate1, slotDateSession1)
    createVisit(sessionTemplate1, slotDateSession1)

    // 1 OPEN and 2 CLOSED visits exist for sessionTemplate2
    createVisit(sessionTemplate2, slotDateSession2, visitRestriction = OPEN)
    createVisit(sessionTemplate2, slotDateSession2, visitRestriction = CLOSED)
    createVisit(sessionTemplate2, slotDateSession2, visitRestriction = CLOSED)

    // sessions 2 and 3 sessions do not go over capacity
    createVisit(sessionTemplate3, visitRestriction = OPEN)
    createVisit(sessionTemplate4, visitRestriction = CLOSED)

    // no visits for any other sessions
    var overBookedSessions = reportingService.getOverbookedSessions(LocalDate.now())
    assertThat(overBookedSessions.size).isEqualTo(2)
    overBookedSessions = overBookedSessions.sortedBy { it.sessionDate.dayOfWeek }
    assertOverbookedSession(overBookedSessions[0], sessionTemplate1, slotDateSession1, expectedOpenCount = 2, expectedClosedCount = 0)
    assertOverbookedSession(overBookedSessions[1], sessionTemplate2, slotDateSession2, expectedOpenCount = 1, expectedClosedCount = 2)

    verify(telemetryClient, times(2)).trackEvent(eq("overbooked-sessions-report"), any(), isNull())
  }

  @Test
  fun `when there are no overbooked sessions empty list is returned`() {
    // Given
    val slotDateSession1 = getNextSlotDate(sessionTemplate1)
    val slotDateSession2 = getNextSlotDate(sessionTemplate2)

    // 1 OPEN visits exist for sessionTemplate1 - not over capacity
    createVisit(sessionTemplate1, slotDateSession1)

    // 1 OPEN and 1 CLOSED visits exist for sessionTemplate2  - not over capacity
    createVisit(sessionTemplate2, slotDateSession2, visitRestriction = OPEN)
    createVisit(sessionTemplate2, slotDateSession2, visitRestriction = CLOSED)

    // sessions 2 and 3 sessions do not go over capacity
    createVisit(sessionTemplate3, visitRestriction = OPEN)
    createVisit(sessionTemplate4, visitRestriction = CLOSED)

    // no visits for any other sessions
    val overBookedSessions = reportingService.getOverbookedSessions(LocalDate.now())
    assertThat(overBookedSessions.size).isEqualTo(0)

    verify(telemetryClient, times(0)).trackEvent(eq("overbooked-sessions-report"), any(), isNull())
  }

  private fun assertOverbookedSession(
    overbookedSession: OverbookedSessionsDto,
    sessionTemplate: SessionTemplate,
    expectedDate: LocalDate,
    expectedOpenCount: Int,
    expectedClosedCount: Int,
  ) {
    with(overbookedSession) {
      assertThat(sessionDate).isEqualTo(expectedDate)
      assertThat(sessionTemplate).isEqualTo(sessionTemplate)
      assertThat(sessionCapacity.open).isEqualTo(sessionTemplate.openCapacity)
      assertThat(sessionCapacity.closed).isEqualTo(sessionTemplate.closedCapacity)
      assertThat(openCount).isEqualTo(expectedOpenCount)
      assertThat(closedCount).isEqualTo(expectedClosedCount)
    }
  }
}
