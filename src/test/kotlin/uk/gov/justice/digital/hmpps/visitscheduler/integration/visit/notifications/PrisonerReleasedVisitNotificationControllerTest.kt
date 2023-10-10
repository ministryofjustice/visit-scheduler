package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerReleasedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.RELEASED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.RELEASED_TO_HOSPITAL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.SENT_TO_COURT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.TRANSFERRED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.ReleaseReasonType.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerHadBeenReleased
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class PrisonerReleasedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has been released from prison then only valid visits are flagged and saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, RELEASED)

    val visit1 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = notificationDto.prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().minusDays(1),
      prisonCode = notificationDto.prisonCode,
      visitStatus = BOOKED,
    )

    val visit3 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().minusDays(1),
      prisonCode = notificationDto.prisonCode,
      visitStatus = CANCELLED,
    )

    val visit4 = visitEntityHelper.create(
      prisonerId = "ANOTHERPRISONER",
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = notificationDto.prisonCode,
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(visit1), NotificationEventType.PRISONER_RELEASED_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(visit1.reference)
  }

  @Test
  fun `when prisoner has been released to hospital then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, RELEASED_TO_HOSPITAL)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been temporary released then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, TEMPORARY_ABSENCE_RELEASE)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been sent to cort then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, SENT_TO_COURT)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has transferred then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, TRANSFERRED)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  @Test
  fun `when prisoner has been released but we dont know why then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerReleasedNotificationDto(prisonerId, prisonCode, UNKNOWN)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerHadBeenReleased(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertNotHandled()
  }

  private fun assertNotHandled() {
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }
}
