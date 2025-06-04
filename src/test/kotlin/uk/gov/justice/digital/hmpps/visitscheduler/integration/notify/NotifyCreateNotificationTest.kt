package uk.gov.justice.digital.hmpps.visitscheduler.integration.notify

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFY_CONTROLLER_CREATE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.NotifyHistoryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCreateNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitHistoryByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import java.time.temporal.ChronoUnit

@DisplayName("Tests for create GOV.UK notify events")
class NotifyCreateNotificationTest : IntegrationTestBase() {
  private val notifyRoles = listOf("ROLE_VISIT_SCHEDULER__VISIT_NOTIFICATION_ALERTS")
  private val visitSchedulerRoles = listOf("ROLE_VISIT_SCHEDULER")

  private lateinit var roleGovNotifyHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleGovNotifyHttpHeaders = setAuthorisation(roles = notifyRoles)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = visitSchedulerRoles)
  }

  @Test
  fun `when notify create notification called and no other notification for notification id exists an entry is created`() {
    // Given
    val application = createApplicationAndSave(applicationStatus = ACCEPTED)
    val visit = createVisitAndSave(VisitStatus.BOOKED, application)
    val eventAudit = eventAuditEntityHelper.create(
      reference = visit.reference,
      text = null,
    )

    val notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = eventAudit.id,
      notificationType = "email",
    )

    val responseSpec = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    assertNotifyHistory(notifyHistory, notifyCreateNotificationDto, NotifyNotificationType.EMAIL)
  }

  @Test
  fun `when notify create notification called and notification already exists for notification id exists no new entry is created`() {
    // Given
    val application = createApplicationAndSave(applicationStatus = ACCEPTED)
    val visit = createVisitAndSave(VisitStatus.BOOKED, application)
    val eventAudit = eventAuditEntityHelper.create(
      reference = visit.reference,
      text = null,
    )

    val notifyCreateNotificationDto1 = createNotifyCreateNotificationDto(
      eventAuditReference = eventAudit.id,
      notificationType = "email",
    )

    val notifyCreateNotificationDto2 = createNotifyCreateNotificationDto(
      notificationId = notifyCreateNotificationDto1.notificationId,
      eventAuditReference = eventAudit.id,
      notificationType = "email",
    )

    val responseSpec1 = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto1, setAuthorisation(roles = notifyRoles))
    responseSpec1.expectStatus().isOk

    val responseSpec2 = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto2, setAuthorisation(roles = notifyRoles))
    responseSpec2.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    assertNotifyHistory(notifyHistory, notifyCreateNotificationDto1, NotifyNotificationType.EMAIL)
  }

  @Test
  fun `when event audit for notification does not exist validation error is returned`() {
    // Given
    val notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = RandomUtils.nextLong(),
      notificationType = "email",
    )

    val responseSpec = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isEqualTo(400)
  }

  @Test
  fun `create notify event - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = 1234,
      notificationType = "email",
    )

    // When
    val responseSpec = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `create notify event - access forbidden when incorrect role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = visitSchedulerRoles)
    val notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = 1234,
      notificationType = "email",
    )

    // When
    val responseSpec = callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `create notify event - access forbidden when no token`() {
    // Given
    val notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = 1234,
      notificationType = "email",
    )

    val jsonBody = BodyInserters.fromValue(notifyCreateNotificationDto)

    // When
    val responseSpec = webTestClient.post().uri(VISIT_NOTIFY_CONTROLLER_CREATE_PATH)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun assertNotifyHistory(
    notifyHistory: NotifyHistoryDto,
    notifyCreateNotificationDto: NotifyCreateNotificationDto,
    notificationType: NotifyNotificationType,
  ) {
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(notifyCreateNotificationDto.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(notificationType)
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.SENDING)
    Assertions.assertThat(notifyHistory.createdAt?.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(notifyCreateNotificationDto.createdAt.truncatedTo(ChronoUnit.SECONDS))
    Assertions.assertThat(notifyHistory.completedAt).isNull()
    Assertions.assertThat(notifyHistory.sentAt).isNull()
  }
}
