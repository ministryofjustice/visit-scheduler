package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = Propagation.SUPPORTS)
@DisplayName("OldVisit Counts Reporting")
class ReportVisitCountsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var reportingTask: ReportingTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private val reportDate = LocalDate.now().minusDays(1)

  lateinit var prison1: Prison
  lateinit var prison2: Prison
  lateinit var prison3: Prison
  lateinit var prison4: Prison

  lateinit var sessionTemplate1Prison1: SessionTemplate
  lateinit var sessionTemplate2Prison1: SessionTemplate
  lateinit var sessionTemplate3Prison1: SessionTemplate
  lateinit var sessionTemplate4Prison1: SessionTemplate
  lateinit var sessionTemplate5Prison1: SessionTemplate
  lateinit var sessionTemplate6Prison1: SessionTemplate
  lateinit var sessionTemplate7Prison1: SessionTemplate

  @BeforeEach
  fun setupData() {
    vsipReportingEntityHelper.create(VSIPReport.VISIT_COUNTS_BY_DAY, reportDate.minusDays(1))
    // active prison with multiple sessions
    prison1 = prisonEntityHelper.create("ABC", activePrison = true, excludeDates = emptyList())
    // expired session
    sessionTemplate1Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.minusDays(7), validToDate = reportDate.minusDays(1))
    // future session
    sessionTemplate2Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.plusDays(1))
    // valid session
    sessionTemplate3Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.minusWeeks(7), weeklyFrequency = 1, dayOfWeek = reportDate.dayOfWeek)
    // valid session but not for same report date
    sessionTemplate4Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.minusWeeks(7), weeklyFrequency = 1, dayOfWeek = reportDate.dayOfWeek.plus(1))
    // valid session but not same weekly frequency
    sessionTemplate5Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.minusWeeks(3), weeklyFrequency = 2, dayOfWeek = reportDate.dayOfWeek)
    // valid session with visits
    sessionTemplate6Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate, weeklyFrequency = 1, dayOfWeek = reportDate.dayOfWeek, startTime = LocalTime.of(15, 0), endTime = LocalTime.of(16, 0))
    // valid session with visits
    sessionTemplate7Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate, weeklyFrequency = 1, dayOfWeek = reportDate.dayOfWeek, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0))
    // exclude report date
    prison2 = prisonEntityHelper.create("DEF", activePrison = true, excludeDates = listOf(reportDate))

    // active prison with no sessions for report date
    prison3 = prisonEntityHelper.create("GHI", activePrison = true, excludeDates = emptyList())

    // inactive prison
    prison4 = prisonEntityHelper.create("JKL", activePrison = false, excludeDates = emptyList())

    // visit 1 against sessionTemplate6Prison1, OPEN and BOOKED - included in openBookedCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 2 against sessionTemplate6Prison1, OPEN and BOOKED - included in openBookedCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 3 against sessionTemplate6Prison1, OPEN and BOOKED - included in openBookedCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 4 against sessionTemplate6Prison1, CLOSED and BOOKED - included in closedBookedCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime), visitRestriction = VisitRestriction.CLOSED)
    // visit 5 against sessionTemplate6Prison1, OPEN and RESERVED - not included in counts
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.RESERVED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 6 against sessionTemplate6Prison1, OPEN and CANCELLED - included in openCancelledCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime), outcomeStatus = OutcomeStatus.ADMINISTRATIVE_CANCELLATION)
    // visit 7 against sessionTemplate6Prison1, OPEN and CANCELLED but SUPERSEDED_CANCELLATION - not included in closedBookedCount
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, sessionTemplateReference = sessionTemplate6Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime), outcomeStatus = OutcomeStatus.SUPERSEDED_CANCELLATION)

    // visit 1 against sessionTemplate7Prison1, OPEN and BOOKED
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate7Prison1.reference, visitStart = reportDate.atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 2 against sessionTemplate7Prison1 - but previous week - not included in count
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate7Prison1.reference, visitStart = reportDate.minusWeeks(1).atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
    // visit 3 against sessionTemplate7Prison1 - but previous week - not included in count
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplateReference = sessionTemplate7Prison1.reference, visitStart = reportDate.plusWeeks(1).atTime(sessionTemplate6Prison1.startTime), visitEnd = reportDate.atTime(sessionTemplate6Prison1.endTime))
  }

  @Test
  fun `when session report called for a report date then a valid session report is returned`() {
    // Given
    val sessionsReport = reportingTask.getVisitCountsReportByDay()[reportDate]!!
    Assertions.assertThat(sessionsReport.size).isEqualTo(5)

    val session3Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate3Prison1.reference)!!
    assertSessionVisitCounts(session3Prison1, reportDate, prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate3Prison1, 0, 0, 0, 0)

    val session6Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate6Prison1.reference)!!
    assertSessionVisitCounts(session6Prison1, reportDate, prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate6Prison1, 3, 1, 1, 0)

    val session7Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate7Prison1.reference)!!
    assertSessionVisitCounts(session7Prison1, reportDate, prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate7Prison1, 1, 0, 0, 0)

    val prison2Report = getSessionReport(sessionsReport, prison2.code, null)!!
    assertSessionVisitCounts(prison2Report, reportDate, prison2, isBlockedDate = true, hasSessionsOnDate = false, null, 0, 0, 0, 0)

    val prison3Report = getSessionReport(sessionsReport, prison3.code, null)!!
    assertSessionVisitCounts(prison3Report, reportDate, prison3, isBlockedDate = false, hasSessionsOnDate = false, null, 0, 0, 0, 0)

    verify(telemetryClient, times(5)).trackEvent(eq("visit-counts-report"), any(), isNull())
  }

  private fun getSessionReport(sessionReports: List<SessionVisitCountsDto>, prisonCode: String, sessionTemplateReference: String?): SessionVisitCountsDto? {
    return sessionReports.filter { it.prisonCode == prisonCode }.firstOrNull { it.sessionReference == sessionTemplateReference }
  }

  private fun assertSessionVisitCounts(
    sessionVisitCounts: SessionVisitCountsDto,
    reportDate: LocalDate,
    prison: Prison,
    isBlockedDate: Boolean,
    hasSessionsOnDate: Boolean,
    sessionTemplate: SessionTemplate?,
    openBookedCount: Int,
    closedBookedCount: Int,
    openCancelledCount: Int,
    closedCancelledCount: Int,
  ) {
    Assertions.assertThat(sessionVisitCounts.reportDate).isEqualTo(reportDate)
    Assertions.assertThat(sessionVisitCounts.prisonCode).isEqualTo(prison.code)
    Assertions.assertThat(sessionVisitCounts.isBlockedDate).isEqualTo(isBlockedDate)
    Assertions.assertThat(sessionVisitCounts.hasSessionsOnDate).isEqualTo(hasSessionsOnDate)
    sessionTemplate?.let {
      Assertions.assertThat(sessionVisitCounts.sessionReference).isEqualTo(sessionTemplate.reference)
      Assertions.assertThat(sessionVisitCounts.sessionTimeSlot?.startTime).isEqualTo(sessionTemplate.startTime)
      Assertions.assertThat(sessionVisitCounts.sessionTimeSlot?.endTime).isEqualTo(sessionTemplate.endTime)
      Assertions.assertThat(sessionVisitCounts.sessionCapacity?.open).isEqualTo(sessionTemplate.openCapacity)
      Assertions.assertThat(sessionVisitCounts.sessionCapacity?.closed).isEqualTo(sessionTemplate.closedCapacity)
      Assertions.assertThat(sessionVisitCounts.visitType).isEqualTo(sessionTemplate.visitType)
    }
    Assertions.assertThat(sessionVisitCounts.openBookedCount).isEqualTo(openBookedCount)
    Assertions.assertThat(sessionVisitCounts.closedBookedCount).isEqualTo(closedBookedCount)
    Assertions.assertThat(sessionVisitCounts.openCancelledCount).isEqualTo(openCancelledCount)
    Assertions.assertThat(sessionVisitCounts.closedCancelledCount).isEqualTo(closedCancelledCount)
  }
}
