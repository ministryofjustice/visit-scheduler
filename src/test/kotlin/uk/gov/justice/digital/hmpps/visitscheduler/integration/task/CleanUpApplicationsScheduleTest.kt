package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_SLOT_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Transactional(propagation = SUPPORTS)
@DisplayName("Clean K")
class CleanUpApplicationsScheduleTest : IntegrationTestBase() {

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var visitTask: VisitTask

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedVisitNotExpired: Application

  private lateinit var reservedVisitNotExpiredChangingStatus: Application

  private lateinit var reservedVisitExpired: Application

  private lateinit var reservedVisitExpiredChangingStatus: Application

  @MockBean
  private lateinit var lockProvider: JdbcTemplateLockProvider

  @BeforeEach
  internal fun setUp() {
    reservedVisitNotExpired = applicationEntityHelper.create(prisonerId = "NOT_EXPIRED", sessionTemplate = sessionTemplate, completed = false, reservedSlot = true)
    testApplicationRepository.saveAndFlush(reservedVisitNotExpired)

    reservedVisitNotExpiredChangingStatus = applicationEntityHelper.create(prisonerId = "NOT_EXPIRED", sessionTemplate = sessionTemplate, completed = false, reservedSlot = false)

    reservedVisitExpired = applicationEntityHelper.create(prisonerId = "EXPIRED", sessionTemplate = sessionTemplate, completed = false, reservedSlot = true)
    testApplicationRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpired.id)

    reservedVisitExpiredChangingStatus = testApplicationRepository.saveAndFlush(applicationEntityHelper.create(prisonerId = "EXPIRED", sessionTemplate = sessionTemplate, completed = false, reservedSlot = false))
    testApplicationRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(2), reservedVisitExpiredChangingStatus.id)
  }

  @Test
  fun `delete only expired reserved applications`() {
    // Given
    val notExpiredApplicationReference = reservedVisitNotExpired.reference
    val visitExpiredApplicationReference = reservedVisitExpired.reference

    // When
    visitTask.deleteExpiredReservations()

    // Then
    assertThat(testApplicationRepository.findByReference(notExpiredApplicationReference)).isNotNull
    assertThat(testApplicationRepository.findByReference(visitExpiredApplicationReference)).isNull()

    assertDeleteEvent(reservedVisitExpired)
  }

  @Test
  fun `delete only expired changing applications`() {
    // Given
    val notExpiredApplicationReferenceChangingStatus = reservedVisitNotExpiredChangingStatus.reference
    val visitExpiredApplicationReferenceChangingStatus = reservedVisitExpiredChangingStatus.reference

    // When
    visitTask.deleteExpiredReservations()

    // Then
    assertThat(testApplicationRepository.findByReference(notExpiredApplicationReferenceChangingStatus)).isNotNull
    assertThat(testApplicationRepository.findByReference(visitExpiredApplicationReferenceChangingStatus)).isNull()

    assertDeleteEvent(reservedVisitExpiredChangingStatus)
  }

  private fun assertDeleteEvent(application: Application) {
    verify(telemetryClient, times(1)).trackEvent(
      eq(VISIT_SLOT_RELEASED_EVENT.eventName),
      org.mockito.kotlin.check {
        assertThat(it["applicationReference"]).isEqualTo(application.reference)
        assertThat(it["prisonerId"]).isEqualTo(application.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(application.prison.code)
        assertThat(it["visitType"]).isEqualTo(application.visitType.name)
        assertThat(it["visitRestriction"]).isEqualTo(application.restriction.name)
        assertThat(it["visitStart"]).isEqualTo(application.sessionSlot.slotDate.atTime(application.sessionSlot.slotTime).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["reserved"]).isEqualTo(application.reservedSlot.toString())
      },
      isNull(),
    )
  }
}
