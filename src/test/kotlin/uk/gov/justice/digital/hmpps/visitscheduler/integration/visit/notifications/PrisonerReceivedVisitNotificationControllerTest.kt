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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.ADMISSION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.RETURN_FROM_COURT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TEMPORARY_ABSENCE_RETURN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.PrisonerReceivedReasonType.TRANSFERRED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
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
  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has received with reason transfer then only valid visits are flagged and saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, TRANSFERRED, prisonCode)

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
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PRISONER_RECEIVED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].bookingReference).isEqualTo(visit1.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.PRISONER_RECEIVED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(actionedBy).isEqualTo("NOT_KNOWN")
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.PRISONER_RECEIVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(userType).isEqualTo(UserType.STAFF)
    }
  }

  @Test
  fun `when prisoner has been received due to temporary absence return then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, TEMPORARY_ABSENCE_RETURN, prisonCode)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been received due to return from court then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, RETURN_FROM_COURT, prisonCode)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReceived(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been received due to admission then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReceivedNotificationDto(prisonerId, ADMISSION, prisonCode)

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
