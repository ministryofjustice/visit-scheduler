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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.APPLICATION_DELETED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.task.ApplicationTask
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Transactional(propagation = SUPPORTS)
@DisplayName("Clean up applications")
class CleanUpApplicationsScheduleTest : IntegrationTestBase() {

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var applicationTask: ApplicationTask

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedVisitNotExpired: Application

  private lateinit var reservedVisitNotExpiredChangingStatus: Application

  private lateinit var reservedVisitExpired: Application

  private lateinit var reservedVisitExpiredChangingStatus: Application

  @MockitoBean
  private lateinit var lockProvider: JdbcTemplateLockProvider

  @BeforeEach
  internal fun setUp() {
    reservedVisitNotExpired = createApplicationAndSave(prisonerId = "NOT_EXPIRED", sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, reservedSlot = true)
    reservedVisitNotExpiredChangingStatus = createApplicationAndSave(prisonerId = "NOT_EXPIRED", sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, reservedSlot = false)

    reservedVisitExpired = createApplicationAndSave(prisonerId = "EXPIRED", sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, reservedSlot = true)
    testApplicationRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(25), reservedVisitExpired.id)

    reservedVisitExpiredChangingStatus = createApplicationAndSave(prisonerId = "EXPIRED", sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, reservedSlot = false)
    testApplicationRepository.updateModifyTimestamp(LocalDateTime.now().minusHours(24), reservedVisitExpiredChangingStatus.id)
  }

  @Test
  fun `delete only expired reserved applications`() {
    // Given
    // When
    applicationTask.deleteExpiredApplications()

    // Then
    assertApplicationExistAndChildObjects(reservedVisitNotExpired)
    assertApplicationAndChildObjectsDeleted(reservedVisitExpired)
    assertDeleteEvent(reservedVisitExpired)
  }

  @Test
  fun `delete only expired changing applications`() {
    // Given

    // When
    applicationTask.deleteExpiredApplications()

    // Then
    assertApplicationExistAndChildObjects(reservedVisitNotExpiredChangingStatus)
    assertApplicationAndChildObjectsDeleted(reservedVisitExpiredChangingStatus)
    assertDeleteEvent(reservedVisitExpiredChangingStatus)
  }

  private fun assertDeleteEvent(application: Application) {
    verify(telemetryClient, times(1)).trackEvent(
      eq(APPLICATION_DELETED_EVENT.eventName),
      org.mockito.kotlin.check {
        assertThat(it["applicationReference"]).isEqualTo(application.reference)
        assertThat(it["prisonerId"]).isEqualTo(application.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(application.prison.code)
        assertThat(it["visitType"]).isEqualTo(application.visitType.name)
        assertThat(it["visitRestriction"]).isEqualTo(application.restriction.name)
        assertThat(it["visitStart"]).isEqualTo(application.sessionSlot.slotStart.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["reserved"]).isEqualTo(application.reservedSlot.toString())
      },
      isNull(),
    )
  }

  private fun assertApplicationExistAndChildObjects(application: Application) {
    assertThat(testApplicationRepository.findByReference(application.reference)).isNotNull
    assertThat(testApplicationRepository.hasVisitorsByApplicationId(application.id)).isTrue()
    assertThat(testApplicationRepository.hasSupportByApplicationId(application.id)).isTrue()
    assertThat(testApplicationRepository.hasContactByApplicationId(application.id)).isTrue()
  }

  private fun assertApplicationAndChildObjectsDeleted(application: Application) {
    assertThat(testApplicationRepository.findByReference(application.reference)).isNull()
    assertThat(testApplicationRepository.hasVisitorsByApplicationId(application.id)).isFalse()
    assertThat(testApplicationRepository.hasSupportByApplicationId(application.id)).isFalse()
    assertThat(testApplicationRepository.hasContactByApplicationId(application.id)).isFalse()
  }
}
