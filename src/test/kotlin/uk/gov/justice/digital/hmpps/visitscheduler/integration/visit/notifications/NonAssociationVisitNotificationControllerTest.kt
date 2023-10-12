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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerService
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH")
class NonAssociationVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var prisonerService: PrisonerService

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId, prisonCode, IncentiveLevel.ENHANCED)
  }

  fun stubGetPrisonerNonAssociationForPrisonApi(
    prisonerId: String = primaryPrisonerId,
    nonAssociationId: String = secondaryPrisonerId,
  ) {
    Companion.nonAssociationsApiMockServer.stubGetPrisonerNonAssociation(
      prisonerId,
      nonAssociationId,
    )
  }

  fun stubGetPrisonerNonAssociationEmpty(
    prisonerId: String = primaryPrisonerId,
  ) {
    Companion.nonAssociationsApiMockServer.stubGetPrisonerNonAssociationEmpty(
      prisonerId,
    )
  }

  @Test
  fun `when prisoners have overlapped visits then visits with same date and prison are flagged and saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val secondaryVisit1 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // visit does not overlap
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    assertBookedEvent(listOf(primaryVisit1, primaryVisit2, secondaryVisit1, secondaryVisit2), NotificationEventType.NON_ASSOCIATION_EVENT)
    verify(telemetryClient, times(4)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(4)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(4)
    val eventRef = visitNotifications[0].reference
    Assertions.assertThat(eventRef).isNotNull()
    Assertions.assertThat(visitNotifications[1].reference).isEqualTo(eventRef)
    Assertions.assertThat(visitNotifications[2].reference).isEqualTo(eventRef)
    Assertions.assertThat(visitNotifications[3].reference).isEqualTo(eventRef)
  }

  @Test
  fun `when two events are consumed they have different references associated for each event`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification1 = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val primaryPrisonerId2 = primaryPrisonerId + "Extp"
    val secondaryPrisonerId2 = secondaryPrisonerId + "Exts"

    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId2, prisonCode, IncentiveLevel.ENHANCED)
    stubGetPrisonerNonAssociationForPrisonApi(primaryPrisonerId2, secondaryPrisonerId2)
    val nonAssociationChangedNotification2 = NonAssociationChangedNotificationDto(primaryPrisonerId2, secondaryPrisonerId2)

    val primaryVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit1)

    val primaryVisit2 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId2,
      visitStart = primaryVisit1.visitStart,
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(primaryVisit2)

    val secondaryVisit1 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = primaryVisit1.prison.code,
      visitStart = primaryVisit1.visitStart,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit1)

    val secondaryVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId2,
      prisonCode = primaryVisit2.prison.code,
      visitStart = primaryVisit1.visitStart,
      visitStatus = BOOKED,
    )
    eventAuditEntityHelper.create(secondaryVisit2)

    // When
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification1)
    callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification2)

    // Then
    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(4)

    with(visitNotifications[0]) {
      Assertions.assertThat(reference).isNotNull()
      Assertions.assertThat(bookingReference).isEqualTo(primaryVisit1.reference)
    }
    with(visitNotifications[1]) {
      Assertions.assertThat(bookingReference).isEqualTo(secondaryVisit1.reference)
      Assertions.assertThat(reference).isEqualTo(visitNotifications[0].reference)
    }
    with(visitNotifications[2]) {
      Assertions.assertThat(reference).isNotNull()
      Assertions.assertThat(bookingReference).isEqualTo(primaryVisit2.reference)
    }
    with(visitNotifications[3]) {
      Assertions.assertThat(bookingReference).isEqualTo(secondaryVisit2.reference)
      Assertions.assertThat(reference).isEqualTo(visitNotifications[2].reference)
    }
  }

  @Test
  fun `when prisoner with non associations visits has existing notification then they are not flagged or saved`() {
    // Given
    val today = LocalDateTime.now()
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = today.plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    val secondaryVisit = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = primaryVisit.visitStart,
      prisonCode = primaryVisit.prison.code,
      visitStatus = BOOKED,
    )

    val firstVisit = testVisitNotificationEventRepository.saveAndFlush(
      VisitNotificationEvent(
        primaryVisit.reference,
        NotificationEventType.NON_ASSOCIATION_EVENT,
      ),
    )

    testVisitNotificationEventRepository.saveAndFlush(
      VisitNotificationEvent(
        secondaryVisit.reference,
        NotificationEventType.NON_ASSOCIATION_EVENT,
        _reference = firstVisit.reference,
      ),
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non associations event is triggered but prisoner has no non associations they are not flagged or saved`() {
    // This can happen when non associations event is triggered by delete or an update
    // Given
    val today = LocalDateTime.now()
    stubGetPrisonerNonAssociationEmpty()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val primaryVisit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = today.plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = primaryVisit.visitStart,
      prisonCode = primaryVisit.prison.code,
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonerService, times(1)).hasPrisonerGotANonAssociationWith(primaryPrisonerId, secondaryPrisonerId)
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when non associations event is triggered but prisoner has no non associations they are not flagged or saved 2`() {
    // This can happen when non associations event is triggered by delete or an update

    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)
    nonAssociationsApiMockServer.stubGetPrisonerNonAssociationHttpError()

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(prisonerService, times(1)).hasPrisonerGotANonAssociationWith(nonAssociationChangedNotification.prisonerNumber, nonAssociationChangedNotification.nonAssociationPrisonerNumber)
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when both prisoners have no overlapping visits then no visits are flagged or saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(5),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(6),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when primary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val visitStart = LocalDate.now()

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = visitStart.minusDays(1).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoners are for today + 4, +5 & +6
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(4),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(5),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = LocalDateTime.now().plusDays(6),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when secondary prisoner has no future visits then no visits are flagged or saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoners are for today + 1, +2 & +3
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(3),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // no visits for secondary prisoners
    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when both prisoners have overlapping visits only in the past then no visits are flagged or saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    val visitStart = LocalDate.now()

    // no visits overlap
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = visitStart.minusDays(1).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = visitStart.minusDays(2).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = visitStart.minusDays(3).atTime(11, 0),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = visitStart.minusDays(1).atTime(11, 0),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = visitStart.minusDays(2).atTime(11, 0),
      visitStatus = BOOKED,
    )

    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = prisonCode,
      visitStart = visitStart.minusDays(3).atTime(11, 0),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when future visits overlap but in different prisons then no visits are flagged or saved`() {
    // Given
    stubGetPrisonerNonAssociationForPrisonApi()
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(primaryPrisonerId, secondaryPrisonerId)

    // no visits overlap
    // visits for primary prisoner is for tomorrow at ABC prison
    visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      prisonCode = prisonCode,
      visitStatus = BOOKED,
    )

    // visits for secondary prisoner is for tomorrow at DEF prison
    visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      prisonCode = "DEF",
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
    )

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }
}
