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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_RECEIVED_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.ADMISSION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TRANSFERRED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReceivedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerHadBeenReceived
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_RECEIVED_CHANGE_PATH")
class PrisonerReceivedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val prisonCode = "ABC"
  val otherPrisonCode = "DEF"

  lateinit var prison1: Prison
  lateinit var prison2: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var otherSessionTemplate: SessionTemplate
  lateinit var otherPrisonSessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    prison2 = prisonEntityHelper.create(prisonCode = otherPrisonCode)

    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    otherSessionTemplate = sessionTemplateEntityHelper.create(prison = prison1)
    otherPrisonSessionTemplate = sessionTemplateEntityHelper.create(prison = prison2)

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has received with reason transfer then only valid visits are flagged and saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, otherPrisonCode, TRANSFERRED)
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId = prisonerId, prisonCode = prisonCode)

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
      sessionTemplate = otherSessionTemplate,
    )
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = otherPrisonSessionTemplate,
    )

    eventAuditEntityHelper.create(visit3)

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

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.PRISONER_RECEIVED_EVENT)

    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(0)
    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes.size).isEqualTo(0)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISONER_RECEIVED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISONER_RECEIVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
    with(auditEvents[1]) {
      assertThat(bookingReference).isEqualTo(visit2.reference)
      assertThat(applicationReference).isEqualTo(visit2.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit2.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISONER_RECEIVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when prisoner has received with reason transfer back to a prison they came from then flagged visits are un-flagged`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, prisonCode, TRANSFERRED)

    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit)

    visitNotificationEventHelper.create(visit = visit, notificationEventType = NotificationEventType.PRISONER_RECEIVED_EVENT)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.PRISONER_RECEIVED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.PRISONER_RETURNED_TO_PRISON.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has been received due to temporary absence return then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, prisonCode, TEMPORARY_ABSENCE_RETURN)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been received due to return from court then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, prisonCode, RETURN_FROM_COURT)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been received due to admission then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, prisonCode, ADMISSION)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  private fun assertNotHandled() {
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(PRISONER_RECEIVED_EVENT)).isEqualTo(0)
  }
}
