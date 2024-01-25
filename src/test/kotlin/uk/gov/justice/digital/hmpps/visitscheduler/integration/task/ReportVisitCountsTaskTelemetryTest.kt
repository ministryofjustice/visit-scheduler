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
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VSIPReport
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.task.ReportingTask
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = Propagation.SUPPORTS)
@DisplayName("Visit Counts Reporting - check events sent")
class ReportVisitCountsTaskTelemetryTest : IntegrationTestBase() {
  @Autowired
  private lateinit var reportingTask: ReportingTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private val reportDate = LocalDate.now().minusDays(1)

  @BeforeEach
  fun setupData() {
    vsipReportingEntityHelper.create(VSIPReport.VISIT_COUNTS_BY_DAY, reportDate.minusDays(1))
  }

  @Test
  fun `when active prisons with no sessions report date and prison details are sent`() {
    // Given
    prisonEntityHelper.create("ABC", activePrison = true, excludeDates = emptyList())
    val sessionsReport = reportingTask.getVisitCountsReportByDay()[reportDate]!!
    Assertions.assertThat(sessionsReport.size).isEqualTo(1)

    verify(telemetryClient, times(1)).trackEvent(eq("visit-counts-report"), any(), isNull())
    assertReportingEvent(
      reportDate = reportDate.format(DateTimeFormatter.ISO_DATE),
      prisonCode = "ABC",
      isBlockedDate = "false",
      hasSessionsOnDate = "false",
    )
  }

  @Test
  fun `when active prisons with sessions report date and prison and session details with visit counts are sent`() {
    // Given
    val prison1 = prisonEntityHelper.create("ABC", activePrison = true, excludeDates = emptyList())
    val session = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = reportDate.minusMonths(3), weeklyFrequency = 1, dayOfWeek = reportDate.dayOfWeek, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(13, 0), openCapacity = 100, closedCapacity = 35)
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.OPEN, sessionTemplate = sessionTemplate)
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, visitRestriction = VisitRestriction.OPEN, sessionTemplate = sessionTemplate, outcomeStatus = OutcomeStatus.ADMINISTRATIVE_CANCELLATION)
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.CLOSED, sessionTemplate = sessionTemplate)
    visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.CANCELLED, visitRestriction = VisitRestriction.CLOSED, sessionTemplate = sessionTemplate, outcomeStatus = OutcomeStatus.ADMINISTRATIVE_CANCELLATION)

    val sessionsReport = reportingTask.getVisitCountsReportByDay()[reportDate]!!
    Assertions.assertThat(sessionsReport.size).isEqualTo(1)

    verify(telemetryClient, times(1)).trackEvent(eq("visit-counts-report"), any(), isNull())
    assertReportingEvent(
      reportDate = reportDate.format(DateTimeFormatter.ISO_DATE),
      prisonCode = "ABC",
      isBlockedDate = "false",
      hasSessionsOnDate = "true",
      sessionStart = "11:00:00",
      sessionEnd = "13:00:00",
      openCapacity = "100",
      closedCapacity = "35",
      openBookedCount = "1",
      openCancelledCount = "1",
      closedBookedCount = "1",
      closedCancelledCount = "1",
    )
  }

  private fun assertReportingEvent(
    reportDate: String,
    prisonCode: String? = null,
    isBlockedDate: String? = null,
    hasSessionsOnDate: String? = null,
    sessionStart: String? = null,
    sessionEnd: String? = null,
    openCapacity: String? = null,
    closedCapacity: String? = null,
    openBookedCount: String = "0",
    closedBookedCount: String = "0",
    openCancelledCount: String = "0",
    closedCancelledCount: String = "0",
  ) {
    verify(telemetryClient).trackEvent(
      eq("visit-counts-report"),
      org.mockito.kotlin.check { event ->
        Assertions.assertThat(event["reportDate"]).isEqualTo(reportDate)
        prisonCode?.let {
          Assertions.assertThat(event["prisonCode"]).isEqualTo(prisonCode)
        }
        isBlockedDate?.let {
          Assertions.assertThat(event["blockedDate"]).isEqualTo(isBlockedDate)
        }
        hasSessionsOnDate?.let {
          Assertions.assertThat(event["hasSessions"]).isEqualTo(hasSessionsOnDate)
        }
        sessionStart?.let {
          Assertions.assertThat(event["sessionStart"]).isEqualTo(sessionStart)
        }
        sessionEnd?.let {
          Assertions.assertThat(event["sessionEnd"]).isEqualTo(sessionEnd)
        }
        openCapacity?.let {
          Assertions.assertThat(event["openCapacity"]).isEqualTo(openCapacity)
        }
        closedCapacity?.let {
          Assertions.assertThat(event["closedCapacity"]).isEqualTo(closedCapacity)
        }

        Assertions.assertThat(event["openBooked"]).isEqualTo(openBookedCount)
        Assertions.assertThat(event["closedBooked"]).isEqualTo(closedBookedCount)
        Assertions.assertThat(event["openCancelled"]).isEqualTo(openCancelledCount)
        Assertions.assertThat(event["closedCancelled"]).isEqualTo(closedCancelledCount)
      },
      isNull(),
    )
  }
}
