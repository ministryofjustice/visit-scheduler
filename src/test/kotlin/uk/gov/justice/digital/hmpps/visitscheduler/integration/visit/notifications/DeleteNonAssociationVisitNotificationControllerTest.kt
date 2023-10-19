package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.incentive.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.service.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_DELETED")
class DeleteNonAssociationVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private val nonAssociationDomainEventType = NON_ASSOCIATION_DELETED

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
  fun `when prisoners have no current non association but prisoners has future notifications then notifications are removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(0)
  }

  @Test
  fun `when prisoners have no current non association but notifications are in the past then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()

    val visitPastPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPastPrimary)

    val visitPastSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().minusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPastSecondary)

    val pastVisitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPastPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPastSecondary.reference, NON_ASSOCIATION_EVENT, _reference = pastVisitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(2)
    with(visitNotifications[0]) {
      Assertions.assertThat(reference).isNotNull()
      Assertions.assertThat(bookingReference).isEqualTo(visitPastPrimary.reference)
      Assertions.assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
    }
    with(visitNotifications[1]) {
      Assertions.assertThat(bookingReference).isEqualTo(visitPastSecondary.reference)
      Assertions.assertThat(reference).isEqualTo(visitNotifications[0].reference)
      Assertions.assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
    }
  }

  @Test
  fun `when prisoners have no current non association but no notification group for both prisoners then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitSecondary)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(2)
  }

  @Test
  fun `when prisoners have current non association then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationForPrisonApi()

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(2)

    verifyNoInteractions(visitNotificationEventRepository)
  }
}
