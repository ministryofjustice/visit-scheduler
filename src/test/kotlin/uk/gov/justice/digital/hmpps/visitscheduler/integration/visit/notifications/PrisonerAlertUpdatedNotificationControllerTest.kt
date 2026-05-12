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
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_ALERT_UPDATED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.alerts.AlertDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISONER_ALERT_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerAlertHasBeenUpdated
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_ALERT_UPDATED_PATH")
class PrisonerAlertUpdatedNotificationControllerTest : NotificationTestBase() {
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
  fun `when prisoner has had an alert updated then only associated future booked visits for the prisoner are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    val expectedEventAttributes = mapOf(
      NotificationEventAttributeType.ALERT_CODE to notificationDto.alertCode,
      NotificationEventAttributeType.ALERT_UUID to notificationDto.alertUuid,
    )

    // this visit should be flagged - future booked
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    // this visit should not be flagged as it's in the past
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // this visit should not be flagged as it's in the past and is CANCELLED
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // this visit should not be flagged as it's not for the same prisoner
    createApplicationAndVisit(
      prisonerId = "ANOTHERPRISONER",
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // canceled visit - should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // canceled visit - should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    alertsApiMockServer.stubGetAlertDetails(notificationDto.alertUuid, AlertDto(notificationDto.alertUuid, true))

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertNotificationEvent(visitNotifications[0], visit1, expectedEventAttributes)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    assertAuditEvent(auditEvents[0], visit = visit1, eventType = PRISONER_ALERT_UPDATED_EVENT)

    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PRISONER_ALERT_UPDATED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(1)).getAlertByUuid(notificationDto.alertUuid)
  }

  @Test
  fun `when prisoner has had an alert updated then associated future booked visits across prisons for the prisoner are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    val expectedEventAttributes = mapOf(
      NotificationEventAttributeType.ALERT_CODE to notificationDto.alertCode,
      NotificationEventAttributeType.ALERT_UUID to notificationDto.alertUuid,
    )

    // this visit should be flagged - future booked
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    // this visit should be flagged - future booked different prison
    val visit2 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(visit2)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertNotificationEvent(visitNotifications[0], visit1, expectedEventAttributes)
    assertNotificationEvent(visitNotifications[1], visit2, expectedEventAttributes)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    assertAuditEvent(auditEvents[0], visit = visit1, eventType = PRISONER_ALERT_UPDATED_EVENT)
    assertAuditEvent(auditEvents[1], visit = visit2, eventType = PRISONER_ALERT_UPDATED_EVENT)

    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.PRISONER_ALERT_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(1)).getAlertByUuid(notificationDto.alertUuid)
  }

  @Test
  fun `when prisoner has had an alert updated but no future booked visits then no visits are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    // this visit should not be flagged as it's in the past
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // this visit should not be flagged as it's in the past and is CANCELLED
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // this visit should not be flagged as it's not for the same prisoner
    createApplicationAndVisit(
      prisonerId = "ANOTHERPRISONER",
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // canceled visit - should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // canceled visit - should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(0)
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(0)).getAlertByUuid(notificationDto.alertUuid)
  }

  @Test
  fun `when prisoner has had an alert updated but alert is inactive then no future booked visits for the prisoner are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    // future booked visit
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    // the alert is inactive - so no visits should be flagged
    alertsApiMockServer.stubGetAlertDetails(notificationDto.alertUuid, AlertDto(notificationDto.alertUuid, false))

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(0)
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(1)).getAlertByUuid(notificationDto.alertUuid)
  }

  @Test
  fun `when prisoner has had an alert updated but alerts-api returns a NOT_FOUND then too any future booked visits for the prisoner are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    val expectedEventAttributes = mapOf(
      NotificationEventAttributeType.ALERT_CODE to notificationDto.alertCode,
      NotificationEventAttributeType.ALERT_UUID to notificationDto.alertUuid,
    )

    // future booked visit
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    // the alert is inactive - so no visits should be flagged
    alertsApiMockServer.stubGetAlertDetails(notificationDto.alertUuid, null, HttpStatus.NOT_FOUND)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertNotificationEvent(visitNotifications[0], visit1, expectedEventAttributes)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    assertAuditEvent(auditEvents[0], visit = visit1, eventType = PRISONER_ALERT_UPDATED_EVENT)

    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PRISONER_ALERT_UPDATED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(1)).getAlertByUuid(notificationDto.alertUuid)
  }

  @Test
  fun `when prisoner has had an alert updated but alerts-api returns an INTERNAL_SERVER_ERROR then too future booked visits for the prisoner are flagged`() {
    // Given
    val notificationDto = PrisonerAlertNotificationDto(
      prisonerNumber = prisonerId,
      alertCode = "C1",
      alertUuid = "1234-5678-abcd",
      description = "alert updated",
    )

    val expectedEventAttributes = mapOf(
      NotificationEventAttributeType.ALERT_CODE to notificationDto.alertCode,
      NotificationEventAttributeType.ALERT_UUID to notificationDto.alertUuid,
    )

    // future booked visit
    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    // the alert is inactive - so no visits should be flagged
    alertsApiMockServer.stubGetAlertDetails(notificationDto.alertUuid, null, HttpStatus.INTERNAL_SERVER_ERROR)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertNotificationEvent(visitNotifications[0], visit1, expectedEventAttributes)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERT_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    assertAuditEvent(auditEvents[0], visit = visit1, eventType = PRISONER_ALERT_UPDATED_EVENT)

    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PRISONER_ALERT_UPDATED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(alertsApiClientSpy, times(1)).getAlertByUuid(notificationDto.alertUuid)
  }

  private fun assertNotificationEvent(
    visitNotificationEvent: VisitNotificationEvent,
    visit: Visit,
    expectedEventAttributes: Map<NotificationEventAttributeType, String>,
  ) {
    assertThat(visitNotificationEvent.bookingReference).isEqualTo(visit.reference)
    val eventAttributes = visitNotificationEvent.visitNotificationEventAttributes.associate { it.attributeName to it.attributeValue }
    assertThat(visitNotificationEvent.visitNotificationEventAttributes).hasSize(2)
    assertThat(eventAttributes).containsExactlyInAnyOrderEntriesOf(expectedEventAttributes)
  }

  private fun assertAuditEvent(
    eventAudit: EventAudit,
    userName: String? = null,
    bookerReference: String? = null,
    userType: UserType = SYSTEM,
    visit: Visit,
    eventType: EventAuditType,
    applicationMethodType: ApplicationMethodType = NOT_KNOWN,
  ) {
    with(eventAudit) {
      assertThat(actionedBy.userName).isEqualTo(userName)
      assertThat(actionedBy.bookerReference).isEqualTo(bookerReference)
      assertThat(actionedBy.userType).isEqualTo(userType)
      assertThat(bookingReference).isEqualTo(visit.reference)
      assertThat(applicationReference).isEqualTo(visit.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(eventType)
      assertThat(applicationMethodType).isEqualTo(applicationMethodType)
    }
  }
}
