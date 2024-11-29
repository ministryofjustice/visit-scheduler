package uk.gov.justice.digital.hmpps.visitscheduler.integration.notify

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyNotificationType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.notify.NotifyStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCallbackNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.notify.NotifyCreateNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitHistoryByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNotifyHistory
import java.time.LocalDateTime
import java.util.UUID.randomUUID

@DisplayName("Tests for create GOV.UK notify events")
class NotifyCallbackNotificationTest : IntegrationTestBase() {
  private val notifyRoles = listOf("ROLE_VISIT_SCHEDULER__VISIT_NOTIFICATION_ALERTS")
  private val visitSchedulerRoles = listOf("ROLE_VISIT_SCHEDULER")

  private lateinit var roleGovNotifyHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  private lateinit var visit: Visit
  private lateinit var eventAudit: EventAudit
  private lateinit var notifyCreateNotificationDto: NotifyCreateNotificationDto

  @BeforeEach
  internal fun setUp() {
    roleGovNotifyHttpHeaders = setAuthorisation(roles = notifyRoles)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = visitSchedulerRoles)

    val application = createApplicationAndSave(completed = true)
    visit = createVisitAndSave(VisitStatus.BOOKED, application)
    eventAudit = eventAuditEntityHelper.create(
      reference = visit.reference,
      text = null,
    )

    notifyCreateNotificationDto = createNotifyCreateNotificationDto(
      eventAuditReference = eventAudit.id,
      notificationType = "email",
    )
  }

  @Test
  fun `when notify callback notification called after create with status of delivered an entry is created with status as DELIVERED`() {
    // Given
    val notifyCallbackDto = createCallbackNotificationDto(
      notificationId = notifyCreateNotificationDto.notificationId,
      eventAuditReference = eventAudit.id,
      notificationType = "email",
      notificationStatus = "delivered",
    )

    // this create should get overwritten
    callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, setAuthorisation(roles = notifyRoles)).expectStatus().isOk
    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(notifyCallbackDto.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(NotifyNotificationType.EMAIL)
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.DELIVERED)
    Assertions.assertThat(notifyHistory.createdAt).isEqualTo(notifyCallbackDto.createdAt)
    Assertions.assertThat(notifyHistory.completedAt).isEqualTo(notifyCallbackDto.completedAt)
    Assertions.assertThat(notifyHistory.sentAt).isEqualTo(notifyCallbackDto.sentAt)
  }

  @Test
  fun `when notify callback notification called with status of temporary-failure an entry is created with status as FAILED`() {
    // Given
    val notifyCallbackDto = createCallbackNotificationDto(
      notificationId = notifyCreateNotificationDto.notificationId,
      eventAuditReference = eventAudit.id,
      notificationType = "sms",
      notificationStatus = "temporary-failure",
    )

    callCreateNotifyNotification(webTestClient, notifyCreateNotificationDto, setAuthorisation(roles = notifyRoles)).expectStatus().isOk
    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(notifyCallbackDto.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(NotifyNotificationType.SMS)
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.FAILED)
    Assertions.assertThat(notifyHistory.createdAt).isEqualTo(notifyCallbackDto.createdAt)
    Assertions.assertThat(notifyHistory.completedAt).isEqualTo(notifyCallbackDto.completedAt)
    Assertions.assertThat(notifyHistory.sentAt).isEqualTo(notifyCallbackDto.sentAt)
  }

  @Test
  fun `when notify callback notification called with status of temporary-failure an entry is created with status as UNKNOWN`() {
    // Given
    val notifyCallbackDto = createCallbackNotificationDto(
      eventAuditReference = eventAudit.id,
      notificationType = "email",
      notificationStatus = "unknown",
    )

    // create not called
    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(notifyCallbackDto.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(NotifyNotificationType.get(notifyCallbackDto.notificationType))
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.UNKNOWN)
    Assertions.assertThat(notifyHistory.createdAt).isEqualTo(notifyCallbackDto.createdAt)
    Assertions.assertThat(notifyHistory.completedAt).isEqualTo(notifyCallbackDto.completedAt)
    Assertions.assertThat(notifyHistory.sentAt).isEqualTo(notifyCallbackDto.sentAt)
  }

  @Test
  fun `when both email and sms sent 2 notifications exist for an event audit`() {
    // Given
    val notifyCallbackSms = createCallbackNotificationDto(
      notificationId = "1",
      eventAuditReference = eventAudit.id,
      notificationType = "sms",
      notificationStatus = "temporary-failure",
    )

    val notifyCallbackEmail = createCallbackNotificationDto(
      notificationId = "2",
      eventAuditReference = eventAudit.id,
      notificationType = "email",
      notificationStatus = "delivered",
    )

    val responseSpec1 = callNotifyCallbackNotification(webTestClient, notifyCallbackSms, setAuthorisation(roles = notifyRoles))
    responseSpec1.expectStatus().isOk

    val responseSpec2 = callNotifyCallbackNotification(webTestClient, notifyCallbackEmail, setAuthorisation(roles = notifyRoles))
    responseSpec2.expectStatus().isOk

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(2)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(notifyCallbackSms.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(NotifyNotificationType.SMS)
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.FAILED)
    Assertions.assertThat(notifyHistory.createdAt).isEqualTo(notifyCallbackSms.createdAt)
    Assertions.assertThat(notifyHistory.completedAt).isEqualTo(notifyCallbackSms.completedAt)
    Assertions.assertThat(notifyHistory.sentAt).isEqualTo(notifyCallbackSms.sentAt)

    val notifyHistory2 = eventAuditList[0].notifyHistory[1]
    Assertions.assertThat(notifyHistory2.notificationId).isEqualTo(notifyCallbackEmail.notificationId)
    Assertions.assertThat(notifyHistory2.notificationType).isEqualTo(NotifyNotificationType.EMAIL)
    Assertions.assertThat(notifyHistory2.status).isEqualTo(NotifyStatus.DELIVERED)
    Assertions.assertThat(notifyHistory2.createdAt).isEqualTo(notifyCallbackEmail.createdAt)
    Assertions.assertThat(notifyHistory2.completedAt).isEqualTo(notifyCallbackEmail.completedAt)
    Assertions.assertThat(notifyHistory2.sentAt).isEqualTo(notifyCallbackEmail.sentAt)
  }

  @Test
  fun `when an entry for create and callback both exist the first entry is always the higher status`() {
    // Given
    val createVisitNotifyHistory = VisitNotifyHistory(
      eventAuditId = eventAudit.id,
      notificationId = "1",
      notificationType = NotifyNotificationType.EMAIL,
      templateId = "T1",
      templateVersion = "v1",
      status = NotifyStatus.SENDING,
      sentAt = LocalDateTime.now(),
      completedAt = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      eventAudit = eventAudit,
    )

    val callbackVisitNotifyHistory = VisitNotifyHistory(
      eventAuditId = eventAudit.id,
      notificationId = "1",
      notificationType = NotifyNotificationType.EMAIL,
      templateId = "T2",
      templateVersion = "v2",
      status = NotifyStatus.DELIVERED,
      sentAt = LocalDateTime.now(),
      completedAt = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      eventAudit = eventAudit,
    )

    visitNotifyHistoryHelper.create(createVisitNotifyHistory)
    visitNotifyHistoryHelper.create(callbackVisitNotifyHistory)

    // When
    val responseSpecVisitHistoryByReference = callVisitHistoryByReference(webTestClient, visit.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpecVisitHistoryByReference.expectStatus().isOk
    val eventAuditList = getEventAuditList(responseSpecVisitHistoryByReference)

    Assertions.assertThat(eventAuditList.size).isEqualTo(1)

    // only entry needs to be DELIVERED
    Assertions.assertThat(eventAuditList[0].notifyHistory.size).isEqualTo(1)
    val notifyHistory = eventAuditList[0].notifyHistory.first()
    Assertions.assertThat(notifyHistory.notificationId).isEqualTo(callbackVisitNotifyHistory.notificationId)
    Assertions.assertThat(notifyHistory.notificationType).isEqualTo(NotifyNotificationType.EMAIL)
    Assertions.assertThat(notifyHistory.status).isEqualTo(NotifyStatus.DELIVERED)
    Assertions.assertThat(notifyHistory.createdAt).isEqualTo(callbackVisitNotifyHistory.createdAt)
    Assertions.assertThat(notifyHistory.completedAt).isEqualTo(callbackVisitNotifyHistory.completedAt)
    Assertions.assertThat(notifyHistory.sentAt).isEqualTo(callbackVisitNotifyHistory.sentAt)
  }

  @Test
  fun `when event audit for notification does not exist validation error is returned`() {
    // Given
    val notifyCallbackDto = createCallbackNotificationDto(
      eventAuditReference = RandomUtils.nextLong(),
      notificationType = "email",
      notificationStatus = "delivered",
    )

    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, setAuthorisation(roles = notifyRoles))
    responseSpec.expectStatus().isEqualTo(400)
  }

  @Test
  fun `callback notify event - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val notifyCallbackDto = createCallbackNotificationDto(
      eventAuditReference = 1234,
      notificationType = "email",
      notificationStatus = "delivered",
    )

    // When
    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `create notify event - access forbidden when incorrect role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = visitSchedulerRoles)
    val notifyCallbackDto = createCallbackNotificationDto(
      eventAuditReference = 1234,
      notificationType = "email",
      notificationStatus = "delivered",
    )

    // When
    val responseSpec = callNotifyCallbackNotification(webTestClient, notifyCallbackDto, authHttpHeaders)

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
    val responseSpec = webTestClient.post().uri(VISIT_NOTIFY_CONTROLLER_CALLBACK_PATH)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun createCallbackNotificationDto(
    notificationId: String = randomUUID().toString(),
    eventAuditReference: Long,
    createdAt: LocalDateTime = LocalDateTime.now(),
    completedAt: LocalDateTime = LocalDateTime.now(),
    sentAt: LocalDateTime = LocalDateTime.now(),
    notificationType: String,
    templateID: String = "template-id",
    templateVersion: String = "v1",
    notificationStatus: String,
  ): NotifyCallbackNotificationDto {
    return NotifyCallbackNotificationDto(
      notificationId = notificationId,
      eventAuditReference = eventAuditReference.toString(),
      createdAt = createdAt,
      notificationType = notificationType,
      templateId = templateID,
      templateVersion = templateVersion,
      status = notificationStatus,
      completedAt = completedAt,
      sentAt = sentAt,
    )
  }
}
