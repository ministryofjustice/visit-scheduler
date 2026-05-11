package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_ALERT_ADDED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerAlertHasBeenDeleted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_ALERT_ADDED_PATH")
class PrisonerAlertDeletedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val prisonCode1 = "ABC"
  val prisonCode2 = "XYZ"
  lateinit var prison1: Prison
  lateinit var prison2: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var sessionTemplate2: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode1)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)

    prison2 = prisonEntityHelper.create(prisonCode = prisonCode2)
    sessionTemplate2 = sessionTemplateEntityHelper.create(prison = prison2)

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has had an alert deleted then any associated future booked visits flagged for the same UUID are unflagged`() {
    // Given
    val alertUUID = "123456-12345678-1234-4567"
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = alertUUID,
      description = "alert deleted",
    )

    // this visit has a PRISONER_ALERT_ADDED_EVENT event for the same UUID
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)
    val notificationEventAttributes = mutableMapOf(NotificationEventAttributeType.ALERT_UUID to alertUUID)
    visitNotificationEventHelper.create(visit = visit1, notificationEventType = NotificationEventType.PRISONER_ALERT_ADDED_EVENT, notificationAttributes = notificationEventAttributes)

    // this visit has a PRISONER_ALERT_UPDATED_EVENT event for the same UUID
    val visit2 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit2)
    visitNotificationEventHelper.create(visit = visit2, notificationEventType = NotificationEventType.PRISONER_ALERT_UPDATED_EVENT, notificationAttributes = notificationEventAttributes)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).deleteAll(any())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)

    assertUnflaggedVisitEvent(listOf(visit1, visit2), UnFlagEventReason.PRISONER_ALERT_DELETED, NotificationEventType.PRISONER_ALERT_DELETED_EVENT.reviewType)
    verify(telemetryClient, times(2)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has had an alert deleted then associated future booked visits across prisons for the prisoner are flagged`() {
    // Given
    val alertUUID = "123456-12345678-1234-4567"
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = alertUUID,
      description = "alert deleted",
    )

    // this visit has a PRISONER_ALERT_ADDED_EVENT event for the same UUID - in prison1
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)
    val notificationEventAttributes = mutableMapOf(NotificationEventAttributeType.ALERT_UUID to alertUUID)
    visitNotificationEventHelper.create(visit = visit1, notificationEventType = NotificationEventType.PRISONER_ALERT_ADDED_EVENT, notificationAttributes = notificationEventAttributes)

    // this visit has a PRISONER_ALERT_UPDATED_EVENT event for the same UUID - in prison2
    val visit2 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit2)
    visitNotificationEventHelper.create(visit = visit2, notificationEventType = NotificationEventType.PRISONER_ALERT_UPDATED_EVENT, notificationAttributes = notificationEventAttributes)

    // this visit has a PRISONER_ALERT_ADDED_EVENT event for the same UUID - in prison2
    val visit3 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(visit1)
    visitNotificationEventHelper.create(visit = visit3, notificationEventType = NotificationEventType.PRISONER_ALERT_ADDED_EVENT, notificationAttributes = notificationEventAttributes)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).deleteAll(any())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)

    assertUnflaggedVisitEvent(listOf(visit1, visit2, visit3), UnFlagEventReason.PRISONER_ALERT_DELETED, NotificationEventType.PRISONER_ALERT_DELETED_EVENT.reviewType)
    verify(telemetryClient, times(3)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has had an alert deleted but no future booked visits then no visits are flagged`() {
    // Given
    val alertUUID = "123456-12345678-1234-4567"
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = alertUUID,
      description = "alert deleted",
    )

    // this visit has a PRISONER_ALERT_ADDED_EVENT event for the same UUID - in the past
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)
    val notificationEventAttributes = mutableMapOf(NotificationEventAttributeType.ALERT_UUID to alertUUID)
    visitNotificationEventHelper.create(visit = visit1, notificationEventType = NotificationEventType.PRISONER_ALERT_ADDED_EVENT, notificationAttributes = notificationEventAttributes)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).deleteAll(any())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)

    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has had an alert deleted but no future booked visits with same alert UUID then no visits are flagged`() {
    // Given
    val alertUUID = "123456-12345678-1234-4567"
    val existingNotificationAlertUuid = "333333-12345678-1234-4568"

    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = alertUUID,
      description = "alert deleted",
    )

    // this visit has a PRISONER_ALERT_ADDED_EVENT event for a different UUID
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)
    val notificationEventAttributes = mutableMapOf(NotificationEventAttributeType.ALERT_UUID to existingNotificationAlertUuid)
    visitNotificationEventHelper.create(visit = visit1, notificationEventType = NotificationEventType.PRISONER_ALERT_ADDED_EVENT, notificationAttributes = notificationEventAttributes)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).deleteAll(any())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)

    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
