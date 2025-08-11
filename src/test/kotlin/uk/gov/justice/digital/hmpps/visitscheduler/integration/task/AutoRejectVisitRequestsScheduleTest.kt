package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.task.AutoRejectVisitRequestsTask
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("Auto reject visit requests cron tests")
class AutoRejectVisitRequestsScheduleTest : IntegrationTestBase() {

  @Autowired
  private lateinit var testVisitRepository: VisitRepository

  @Autowired
  private lateinit var autoRejectVisitRequestsTask: AutoRejectVisitRequestsTask

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoBean
  private lateinit var lockProvider: JdbcTemplateLockProvider

  private final val prisonerId = "ABC123QQ"

  @Test
  fun `Auto reject visit requests which fit the criteria of auto rejection 0 day booking window`() {
    // Given I have visits for each prison where:
    // 1 is within the rejection window (CURRENT_DATE + prison's policy_notice_days_min + 1)
    // 1 is outside the rejection window
    prisonEntityHelper.create(prisonCode = "MDI", policyNoticeDaysMin = 0, activePrison = true)
    val mdiSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "MDI")

    val bookedVisitNotForRejection = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(1), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, sessionTemplate = mdiSessionTemplate)
    visitEntityHelper.save(bookedVisitNotForRejection)

    val requestVisitForRejectionMdi = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(1), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = mdiSessionTemplate)
    visitEntityHelper.save(requestVisitForRejectionMdi)

    val requestVisitNotForRejectionMdi = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(2), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = mdiSessionTemplate)
    visitEntityHelper.save(requestVisitNotForRejectionMdi)

    // When
    autoRejectVisitRequestsTask.autoRejectRequestVisits()

    // Then
    verify(telemetryClient, times(1)).trackEvent(eq("visit-request-auto-rejected"), any(), isNull())
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(requestVisitForRejectionMdi.reference)
      },
      isNull(),
    )

    val visits = testVisitRepository.findAll()
    assertThat(visits.size).isEqualTo(3)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.size).isEqualTo(1)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.map { it.reference }.first()).isEqualTo(requestVisitForRejectionMdi.reference)
  }

  @Test
  fun `Auto reject visit requests which fit the criteria of auto rejection 1 day booking window`() {
    // Given I have visits for each prison where:
    // 1 is within the rejection window (CURRENT_DATE + prison's policy_notice_days_min + 1)
    // 1 is outside the rejection window
    prisonEntityHelper.create(prisonCode = "HEI", policyNoticeDaysMin = 1, activePrison = true)
    val heiSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "HEI")

    val bookedVisitNotForRejection = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(1), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, sessionTemplate = heiSessionTemplate)
    visitEntityHelper.save(bookedVisitNotForRejection)

    val requestVisitForRejectionHei = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(2), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = heiSessionTemplate)
    visitEntityHelper.save(requestVisitForRejectionHei)

    val requestVisitNotForRejectionHei = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(3), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = heiSessionTemplate)
    visitEntityHelper.save(requestVisitNotForRejectionHei)

    // When
    autoRejectVisitRequestsTask.autoRejectRequestVisits()

    // Then
    verify(telemetryClient, times(1)).trackEvent(eq("visit-request-auto-rejected"), any(), isNull())
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(requestVisitForRejectionHei.reference)
      },
      isNull(),
    )

    val visits = testVisitRepository.findAll()
    assertThat(visits.size).isEqualTo(3)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.size).isEqualTo(1)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.map { it.reference }.first()).isEqualTo(requestVisitForRejectionHei.reference)
  }

  @Test
  fun `Auto reject visit requests which fit the criteria of auto rejection 4 day booking window`() {
    // Given I have visits for each prison where:
    // 1 is within the rejection window (CURRENT_DATE + prison's policy_notice_days_min + 1)
    // 1 is outside the rejection window
    prisonEntityHelper.create(prisonCode = "CFI", policyNoticeDaysMin = 4, activePrison = true)
    val cfiSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "CFI")

    val bookedVisitNotForRejection = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(2), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, sessionTemplate = cfiSessionTemplate)
    visitEntityHelper.save(bookedVisitNotForRejection)

    val requestVisitForRejectionCfi = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(5), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = cfiSessionTemplate)
    visitEntityHelper.save(requestVisitForRejectionCfi)

    val requestVisitNotForRejectionCfi = createApplicationAndVisit(prisonerId = prisonerId, slotDate = LocalDate.now().plusDays(6), visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED, sessionTemplate = cfiSessionTemplate)
    visitEntityHelper.save(requestVisitNotForRejectionCfi)

    // When
    autoRejectVisitRequestsTask.autoRejectRequestVisits()

    // Then
    verify(telemetryClient, times(1)).trackEvent(eq("visit-request-auto-rejected"), any(), isNull())
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(requestVisitForRejectionCfi.reference)
      },
      isNull(),
    )

    val visits = testVisitRepository.findAll()
    assertThat(visits.size).isEqualTo(3)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.size).isEqualTo(1)
    assertThat(visits.filter { it.visitSubStatus == VisitSubStatus.AUTO_REJECTED }.map { it.reference }.first()).isEqualTo(requestVisitForRejectionCfi.reference)
  }
}
