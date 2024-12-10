package uk.gov.justice.digital.hmpps.visitscheduler.integration.session

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
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.admin.ADMIN_PRISONS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ExcludeDateDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.SessionDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddSessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetSessionTemplateExcludeDates
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemoveSessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplateExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateExcludeDateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate

@DisplayName("Admin $ADMIN_PRISONS_PATH")
class SessionTemplateExcludeDatesTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @MockitoSpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @MockitoSpyBean
  lateinit var sessionTemplateExcludeDateRepositorySpy: SessionTemplateExcludeDateRepository

  @MockitoSpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @Autowired
  protected lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  companion object {
    private const val TEST_USER = "TEST_USER"
  }

  @Test
  fun `when get exclude dates called for session template with no exclude dates empty list is returned`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result)
    Assertions.assertThat(excludeDates).isEmpty()
  }

  @Test
  fun `when get exclude dates called for session template with multiple exclude dates list is returned sorted by date desc`() {
    // Given
    val excludeDate1 = LocalDate.of(2021, 1, 21)
    val excludeDate2 = LocalDate.of(2024, 1, 21)
    val excludeDate3 = LocalDate.of(2023, 1, 21)
    val excludeDate4 = LocalDate.of(2024, 12, 21)
    val sessionTemplate = sessionTemplateEntityHelper.create(excludeDates = mutableListOf(excludeDate1, excludeDate2, excludeDate3, excludeDate4))

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result)
    Assertions.assertThat(excludeDates.size).isEqualTo(4)

    // check results are returned sorted by date desc
    Assertions.assertThat(excludeDates[0].excludeDate).isEqualTo(excludeDate4)
    Assertions.assertThat(excludeDates[1].excludeDate).isEqualTo(excludeDate2)
    Assertions.assertThat(excludeDates[2].excludeDate).isEqualTo(excludeDate3)
    Assertions.assertThat(excludeDates[3].excludeDate).isEqualTo(excludeDate1)
  }

  @Test
  fun `when get exclude dates called for non existent session template then NOT_FOUND error code is returned `() {
    // Given
    val nonExistentSessionTemplateReference = "abc-abc-abc"

    // When
    val responseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentSessionTemplateReference)

    // Then
    responseSpec.expectStatus().isNotFound.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:$nonExistentSessionTemplateReference not found")
  }

  @Test
  fun `access forbidden when no role`() {
    // When
    val responseSpec = callGetSessionTemplateExcludeDates(webTestClient, setAuthorisation(roles = listOf()), sessionTemplateReference = "abc-def-ghi")

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // When
    val responseSpec = webTestClient.get().uri("/admin/session-templates/template/TST/exclude-date").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `when add exclude date called with a date not already added then exclude date is successfully added`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val excludeDate = LocalDate.now().plusDays(10)

    // When
    val responseSpec = callAddSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(sessionTemplateExcludeDateRepositorySpy, times(1)).saveAndFlush(any())

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result)
    Assertions.assertThat(excludeDates.size).isEqualTo(1)
    Assertions.assertThat(excludeDates[0].excludeDate).isEqualTo(excludeDate)
    Assertions.assertThat(excludeDates[0].actionedBy).isEqualTo(TEST_USER)
    verify(telemetryClient, times(1)).trackEvent(eq("add-session-exclude-date"), any(), isNull())
  }

  @Test
  fun `when add exclude date called for session template with existing visits then exclude date is successfully added and visits are marked for review`() {
    // Given
    val excludeDate = LocalDate.now().plusDays(10)

    val sessionTemplate1 = sessionTemplateEntityHelper.create()
    val sessionTemplate2 = sessionTemplateEntityHelper.create()

    // existing visit for excludeDate for same session
    val bookedVisitForSameSession = createApplicationAndVisit(sessionTemplate = sessionTemplate1, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // existing visit for excludeDate for different session
    createApplicationAndVisit(sessionTemplate = sessionTemplate2, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // cancelled visit for excludeDate for same session
    createApplicationAndVisit(sessionTemplate = sessionTemplate1, visitStatus = VisitStatus.CANCELLED, slotDate = excludeDate)

    // existing visit different excludeDate for same session
    createApplicationAndVisit(sessionTemplate = sessionTemplate1, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate.plusDays(1))

    // When
    val responseSpec = callAddSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate1.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(sessionTemplateExcludeDateRepositorySpy, times(1)).saveAndFlush(any())
    verify(visitNotificationEventServiceSpy, times(1)).handleAddSessionVisitBlockDate(SessionDateBlockedDto(sessionTemplate1.reference, excludeDate))

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate1.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result)
    Assertions.assertThat(excludeDates.size).isEqualTo(1)
    Assertions.assertThat(excludeDates[0].excludeDate).isEqualTo(excludeDate)
    Assertions.assertThat(excludeDates[0].actionedBy).isEqualTo(TEST_USER)

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()

    // only 1 visit for the same date and session with status of BOOKED will be flagged.
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSameSession.reference)
    verify(telemetryClient, times(1)).trackEvent(eq("add-session-exclude-date"), any(), isNull())
  }

  @Test
  fun `when add exclude date called with a past date then exclude date is not added and BAD_REQUEST is returned`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val excludeDate = LocalDate.now().minusDays(1)

    // When
    val responseSpec = callAddSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot add exclude date $excludeDate to session template - ${sessionTemplate.reference} as it is in the past")
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).save(SessionTemplateExcludeDate(sessionTemplate.id, sessionTemplate, excludeDate, actionedBy = TEST_USER))
    verify(telemetryClient, times(0)).trackEvent(eq("add-exclude-date"), any(), isNull())
  }

  @Test
  fun `when add exclude date called with existing date then exclude date is not added and BAD_REQUEST is returned`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val sessionTemplate = sessionTemplateEntityHelper.create(excludeDates = existingExcludeDates.toMutableList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callAddSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot add exclude date $excludeDate to session template - ${sessionTemplate.reference} as it already exists")
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).save(SessionTemplateExcludeDate(sessionTemplate.id, sessionTemplate, excludeDate, actionedBy = TEST_USER))
    verify(telemetryClient, times(0)).trackEvent(eq("add-exclude-date"), any(), isNull())
  }

  @Test
  fun `when remove exclude date called with existing date then exclude date is successfully removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))

    val sessionTemplate = sessionTemplateEntityHelper.create(excludeDates = existingExcludeDates.toMutableList())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemoveSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(sessionTemplateExcludeDateRepositorySpy, times(1)).deleteBySessionTemplateIdAndExcludeDate(sessionTemplate.id, excludeDate)

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result).map { it.excludeDate }
    Assertions.assertThat(excludeDates).doesNotContain(excludeDate)
    verify(telemetryClient, times(1)).trackEvent(eq("remove-session-exclude-date"), any(), isNull())
  }

  @Test
  fun `when remove exclude date called with existing date then exclude date is successfully removed and any notified visits are removed`() {
    // Given
    val existingExcludeDates = setOf(LocalDate.now(), LocalDate.now().plusDays(7))
    val sessionTemplate = sessionTemplateEntityHelper.create(excludeDates = existingExcludeDates.toMutableList())

    val excludeDate = LocalDate.now().plusDays(7)

    // existing visit for excludeDate in same session template
    val bookedVisitForSamePrison = visitEntityHelper.create(sessionTemplate = sessionTemplate, visitStatus = VisitStatus.BOOKED)

    visitNotificationEventHelper.create(bookedVisitForSamePrison.reference, NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE)

    // When
    val responseSpec = callRemoveSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isOk
    verify(sessionTemplateExcludeDateRepositorySpy, times(1)).deleteBySessionTemplateIdAndExcludeDate(sessionTemplate.id, excludeDate)
    verify(visitNotificationEventServiceSpy, times(1)).handleRemoveSessionVisitBlockDate(SessionDateBlockedDto(sessionTemplate.reference, excludeDate))

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result).map { it.excludeDate }
    Assertions.assertThat(excludeDates).doesNotContain(excludeDate)
    verify(telemetryClient, times(1)).trackEvent(eq("remove-session-exclude-date"), any(), isNull())
  }

  @Test
  fun `when remove exclude date called with non existent date then exclude date is not removed and BAD_REQUEST is returned`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(excludeDates = mutableListOf())
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemoveSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isBadRequest.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Cannot remove exclude date $excludeDate from session template - ${sessionTemplate.reference} as it does not exist")
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).deleteBySessionTemplateIdAndExcludeDate(sessionTemplate.id, excludeDate)

    val getResponseSpec = callGetSessionTemplateExcludeDates(webTestClient, roleVisitSchedulerHttpHeaders, sessionTemplateReference = sessionTemplate.reference)
    val result = getResponseSpec.expectStatus().isOk.expectBody()
    val excludeDates = getPrisonExcludeDates(result)
    Assertions.assertThat(excludeDates).isEmpty()
    verify(telemetryClient, times(0)).trackEvent(eq("remove-exclude-date"), any(), isNull())
  }

  @Test
  fun `when add exclude dates called for non existent session template then NOT_FOUND error code is returned `() {
    // Given
    val nonExistentSessionTemplateReference = "aws-aws-aws"
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callAddSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentSessionTemplateReference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isNotFound.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:$nonExistentSessionTemplateReference not found")
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).save(any())
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).deleteBySessionTemplateIdAndExcludeDate(any(), any())
    verify(telemetryClient, times(0)).trackEvent(eq("add-exclude-date"), any(), isNull())
  }

  @Test
  fun `when remove exclude dates called for non existent session template then NOT_FOUND error code is returned `() {
    // Given
    val nonExistentSessionTemplateReference = "wer-wer-wer"
    val excludeDate = LocalDate.now().plusDays(7)

    // When
    val responseSpec = callRemoveSessionTemplateExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, nonExistentSessionTemplateReference, excludeDate, actionedBy = TEST_USER)

    // Then
    responseSpec.expectStatus().isNotFound.expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:$nonExistentSessionTemplateReference not found")
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).save(any())
    verify(sessionTemplateExcludeDateRepositorySpy, times(0)).deleteBySessionTemplateIdAndExcludeDate(any(), any())
    verify(telemetryClient, times(0)).trackEvent(eq("remove-exclude-date"), any(), isNull())
  }

  private fun getPrisonExcludeDates(returnResult: BodyContentSpec): Array<ExcludeDateDto> {
    return objectMapper.readValue(returnResult.returnResult().responseBody, Array<ExcludeDateDto>::class.java)
  }
}
