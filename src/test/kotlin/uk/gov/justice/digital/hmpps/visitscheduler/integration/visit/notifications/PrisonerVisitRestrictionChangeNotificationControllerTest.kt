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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerRestrictionChangeNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerRestrictionHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class PrisonerVisitRestrictionChangeNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when prisoner has had a restriction change and has associated visits then the visits are flagged and saved`() {
    // Given
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode, IncentiveLevel.ENHANCED)
    val notificationDto = PrisonerRestrictionChangeNotificationDto(prisonerId, LocalDate.now())

    val visit1 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(visit1)

    visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().minusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().minusDays(1),
      prisonCode = prisonCode,
      visitStatus = CANCELLED,
    )

    visitEntityHelper.create(
      prisonerId = "ANOTHERPRISONER",
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatPrisonerRestrictionHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(visit1), NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    verify(telemetryClient, times(1)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(visit1.reference)
  }

  @Test
  fun `when prisoner has had a restriction change and prison location cannot be found then the all visits in all prisons are flagged and saved`() {
    // Given
    val notificationDto = PrisonerRestrictionChangeNotificationDto(prisonerId, LocalDate.now())

    val visit1 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(visit1)

    val visit2 = visitEntityHelper.create(
      prisonerId = notificationDto.prisonerNumber,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = "AWE",
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(visit2)

    // When
    val responseSpec = callNotifyVSiPThatPrisonerRestrictionHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(visit1), NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    verify(telemetryClient, times(2)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(2)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(visit1.reference)
    Assertions.assertThat(visitNotifications[1].bookingReference).isEqualTo(visit2.reference)
  }
}
