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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reporting.SessionVisitCountsByDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = Propagation.SUPPORTS)
@DisplayName("Visit Counts Reporting")
class ReportVisitCountsTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var reportingTask: ReportingTask

  @MockitoSpyBean
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
    deleteEntityHelper.deleteAll()

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
    var visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 323L, visitContact = false)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 324L, visitContact = false)
    visitEntityHelper.save(visit)

    // visit 2 against sessionTemplate6Prison1, OPEN and BOOKED - included in openBookedCount
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 421L, visitContact = true)
    visitEntityHelper.save(visit)

    // visit 3 against sessionTemplate6Prison1, OPEN and BOOKED - included in openBookedCount
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 521L, visitContact = true)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 522L, visitContact = false)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 523L, visitContact = false)
    visitEntityHelper.save(visit)

    // visit 4 against sessionTemplate6Prison1, CLOSED and BOOKED - included in closedBookedCount
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate, visitRestriction = VisitRestriction.CLOSED)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 621L, visitContact = true)
    visitEntityHelper.save(visit)

    // visit 5 against sessionTemplate6Prison1, OPEN and RESERVED - not included in counts
    applicationEntityHelper.create(prisonCode = prison1.code, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate, applicationStatus = ACCEPTED)

    // visit 6 against sessionTemplate6Prison1, OPEN and CANCELLED - included in openCancelledCount
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, visitSubStatus = VisitSubStatus.CANCELLED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate, outcomeStatus = OutcomeStatus.ADMINISTRATIVE_CANCELLATION)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 721L, visitContact = true)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 722L, visitContact = false)
    visitEntityHelper.save(visit)

    // visit 7 against sessionTemplate6Prison1, OPEN and CANCELLED but SUPERSEDED_CANCELLATION - included in closedBookedCount as outcomeStatus does not matter anymore
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, visitSubStatus = VisitSubStatus.CANCELLED, sessionTemplate = sessionTemplate6Prison1, slotDate = reportDate, outcomeStatus = OutcomeStatus.SUPERSEDED_CANCELLATION)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 821L, visitContact = true)
    visitEntityHelper.save(visit)

    // visit 1 against sessionTemplate7Prison1, OPEN and BOOKED
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate7Prison1, slotDate = reportDate)
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 921L, visitContact = true)
    visitEntityHelper.save(visit)

    // visit 2 against sessionTemplate7Prison1 - but previous week - not included in count
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate7Prison1, slotDate = reportDate.minusWeeks(1))
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 1021L, visitContact = true)
    visitEntityHelper.save(visit)

    // visit 3 against sessionTemplate7Prison1 - but previous week - not included in count
    visit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate7Prison1, slotDate = reportDate.plusWeeks(1))
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 1121L, visitContact = true)
    visitEntityHelper.save(visit)
  }

  @Test
  fun `when session report called for a report date then a valid session report is returned`() {
    // Given
    val sessionsReport = reportingTask.getVisitCountsReportByDay()[reportDate]!!
    Assertions.assertThat(sessionsReport.size).isEqualTo(5)

    val session3Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate3Prison1.reference)!!

    assertSessionVisitCounts(sessionVisitCounts = session3Prison1, reportDate = reportDate, prison = prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate = sessionTemplate3Prison1, openBookedCount = 0, openBookedVisitorsCount = 0, closedBookedCount = 0, closedBookedVisitorsCount = 0, openCancelledCount = 0, closedCancelledCount = 0, visitRoom = sessionTemplate3Prison1.visitRoom)

    val session6Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate6Prison1.reference)!!
    assertSessionVisitCounts(sessionVisitCounts = session6Prison1, reportDate = reportDate, prison = prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate = sessionTemplate6Prison1, openBookedCount = 3, openBookedVisitorsCount = 8, closedBookedCount = 1, closedBookedVisitorsCount = 1, openCancelledCount = 2, closedCancelledCount = 0, visitRoom = sessionTemplate6Prison1.visitRoom)

    val session7Prison1 = getSessionReport(sessionsReport, prison1.code, sessionTemplate7Prison1.reference)!!
    assertSessionVisitCounts(sessionVisitCounts = session7Prison1, reportDate = reportDate, prison = prison1, isBlockedDate = false, hasSessionsOnDate = true, sessionTemplate = sessionTemplate7Prison1, openBookedCount = 1, openBookedVisitorsCount = 1, closedBookedCount = 0, closedBookedVisitorsCount = 0, openCancelledCount = 0, closedCancelledCount = 0, visitRoom = sessionTemplate7Prison1.visitRoom)

    val prison2Report = getSessionReport(sessionsReport, prison2.code, null)!!
    assertSessionVisitCountsWhenNoSessions(sessionVisitCounts = prison2Report, reportDate = reportDate, prison = prison2, isBlockedDate = true, hasSessionsOnDate = false)

    val prison3Report = getSessionReport(sessionsReport, prison3.code, null)!!
    assertSessionVisitCountsWhenNoSessions(sessionVisitCounts = prison3Report, reportDate = reportDate, prison = prison3, isBlockedDate = false, hasSessionsOnDate = false)

    verify(telemetryClient, times(5)).trackEvent(eq("visit-counts-report"), any(), isNull())
  }

  private fun getSessionReport(sessionReports: List<SessionVisitCountsByDateDto>, prisonCode: String, sessionTemplateReference: String?): SessionVisitCountsByDateDto? = sessionReports.filter { it.prisonCode == prisonCode }.firstOrNull { it.visitCountBySession?.sessionReference == sessionTemplateReference }

  private fun assertSessionVisitCounts(
    sessionVisitCounts: SessionVisitCountsByDateDto,
    reportDate: LocalDate,
    prison: Prison,
    isBlockedDate: Boolean,
    hasSessionsOnDate: Boolean,
    sessionTemplate: SessionTemplate,
    openBookedCount: Int,
    openBookedVisitorsCount: Int,
    closedBookedCount: Int,
    closedBookedVisitorsCount: Int,
    openCancelledCount: Int,
    closedCancelledCount: Int,
    visitRoom: String,
  ) {
    Assertions.assertThat(sessionVisitCounts.reportDate).isEqualTo(reportDate)
    Assertions.assertThat(sessionVisitCounts.prisonCode).isEqualTo(prison.code)
    Assertions.assertThat(sessionVisitCounts.isBlockedDate).isEqualTo(isBlockedDate)
    Assertions.assertThat(sessionVisitCounts.hasSessionsOnDate).isEqualTo(hasSessionsOnDate)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession).isNotNull
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.sessionReference).isEqualTo(sessionTemplate.reference)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.sessionTimeSlot.startTime).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.sessionTimeSlot.endTime).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.sessionCapacity.open).isEqualTo(sessionTemplate.openCapacity)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.sessionCapacity.closed).isEqualTo(sessionTemplate.closedCapacity)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.visitType).isEqualTo(sessionTemplate.visitType)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.visitRoom).isEqualTo(visitRoom)

    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.openBookedCount).isEqualTo(openBookedCount)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.openBookedVisitorsCount).isEqualTo(openBookedVisitorsCount)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.closedBookedCount).isEqualTo(closedBookedCount)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.closedBookedVisitorsCount).isEqualTo(closedBookedVisitorsCount)

    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.openCancelledCount).isEqualTo(openCancelledCount)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession!!.closedCancelledCount).isEqualTo(closedCancelledCount)
  }

  private fun assertSessionVisitCountsWhenNoSessions(
    sessionVisitCounts: SessionVisitCountsByDateDto,
    reportDate: LocalDate,
    prison: Prison,
    isBlockedDate: Boolean,
    hasSessionsOnDate: Boolean,
  ) {
    Assertions.assertThat(sessionVisitCounts.reportDate).isEqualTo(reportDate)
    Assertions.assertThat(sessionVisitCounts.prisonCode).isEqualTo(prison.code)
    Assertions.assertThat(sessionVisitCounts.isBlockedDate).isEqualTo(isBlockedDate)
    Assertions.assertThat(sessionVisitCounts.hasSessionsOnDate).isEqualTo(hasSessionsOnDate)
    Assertions.assertThat(sessionVisitCounts.visitCountBySession).isNull()
  }
}
