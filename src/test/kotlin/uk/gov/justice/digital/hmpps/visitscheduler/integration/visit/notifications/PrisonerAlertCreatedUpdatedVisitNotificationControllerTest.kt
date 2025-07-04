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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_ALERTS_UPDATED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISONER_ALERTS_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonPrisonCodeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerAlertCreatedUpdatedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_ALERTS_UPDATED_PATH")
class PrisonerAlertCreatedUpdatedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val prisonCode = "ABC"
  val description = "Prisoner alert codes updated"
  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has had an alert created or updated and has associated visits then the visits are flagged and saved`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    val notificationDto = PrisonerAlertCreatedUpdatedNotificationDto(
      prisonerId,
      description,
      listOf("C1"),
      emptyList(),
      emptyList(),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    createApplicationAndVisit(
      prisonerId = "ANOTHERPRISONER",
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // cancelled visit -should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERTS_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISONER_ALERTS_UPDATED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
    }
  }

  @Test
  fun `when prisoner has had an alert created or updated then only visits are flagged with there own notification references`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    val notificationDto = PrisonerAlertCreatedUpdatedNotificationDto(
      prisonerId,
      description,
      listOf("C1"),
      emptyList(),
      emptyList(),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visit3)

    // cancelled visit -should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2, visit3), NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)
    verify(telemetryClient, times(3)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(3)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.getFutureVisitNotificationEvents(prisonCode)
    assertThat(visitNotifications).hasSize(3)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(0)
    assertThat(visitNotifications[0].reference).doesNotContain(visitNotifications[1].reference, visitNotifications[2].reference)
    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes.size).isEqualTo(0)
    assertThat(visitNotifications[1].reference).doesNotContain(visitNotifications[0].reference, visitNotifications[2].reference)
    assertThat(visitNotifications[2].visit.reference).isEqualTo(visit3.reference)
    assertThat(visitNotifications[2].visitNotificationEventAttributes.size).isEqualTo(0)
    assertThat(visitNotifications[2].reference).doesNotContain(visitNotifications[0].reference, visitNotifications[1].reference)
    assertThat(testEventAuditRepository.getAuditCount(PRISONER_ALERTS_UPDATED_EVENT)).isEqualTo(3)
  }

  @Test
  fun `when prisoner has had an alert created or updated and prisoner has a non prison code then the all visits in all prisons are flagged and saved`() {
    // Given
    val nonPrisonCode = NonPrisonCodeType.ADM.name
    val notificationDto = PrisonerAlertCreatedUpdatedNotificationDto(
      prisonerId,
      description,
      listOf("C1"),
      emptyList(),
      emptyList(),
    )
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, nonPrisonCode, IncentiveLevel.ENHANCED)

    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit2)

    // cancelled visit -should not be flagged
    createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_ALERTS_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISONER_ALERTS_UPDATED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
    }
    with(auditEvents[1]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(bookingReference).isEqualTo(visit2.reference)
      assertThat(applicationReference).isEqualTo(visit2.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit2.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISONER_ALERTS_UPDATED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
    }
  }

  @Test
  fun `when prisoner has had a supported alert removed and has no other supported alerts on profile then all visits are un-flagged`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
    )

    val notificationDto = PrisonerAlertCreatedUpdatedNotificationDto(
      prisonerId,
      description,
      emptyList(),
      listOf("C1"),
      emptyList(),
    )

    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit)

    visitNotificationEventHelper.create(visit = visit, notificationEventType = NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.PRISONER_ALERT_CODE_REMOVED.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has had a supported alert removed and but still has other supported alerts on profile then no visits are un-flagged`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(
      prisonerId = prisonerId,
      prisonCode = prisonCode,
    )

    val notificationDto = PrisonerAlertCreatedUpdatedNotificationDto(
      prisonerId,
      description,
      emptyList(),
      listOf("C1"),
      listOf("C2"),
    )

    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit)

    visitNotificationEventHelper.create(visit = visit, notificationEventType = NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerAlertHasBeenCreatedOrUpdated(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(PRISONER_ALERTS_UPDATED_EVENT)).isEqualTo(0)
  }
}
