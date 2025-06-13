package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType.PAIRED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_CLOSED")
class ClosedNonAssociationVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var prisonCode: String
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private val nonAssociationDomainEventType = NON_ASSOCIATION_CLOSED

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonCode = sessionTemplateDefault.prison.code
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
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    visitNotificationEventHelper.create(visit = visitPrimary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitSecondary.reference)))
    visitNotificationEventHelper.create(visit = visitSecondary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitPrimary.reference)))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(0)
  }

  @Test
  fun `when prisoners have no current non association but notifications are in the past then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()

    val visitPastPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPastPrimary)

    val visitPastSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().minusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPastSecondary)

    visitNotificationEventHelper.create(visit = visitPastPrimary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitPastSecondary.reference)))
    visitNotificationEventHelper.create(visit = visitPastSecondary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitPastPrimary.reference)))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
    with(visitNotifications[0]) {
      Assertions.assertThat(reference).isNotNull()
      Assertions.assertThat(visit.reference).isEqualTo(visitPastPrimary.reference)
      Assertions.assertThat(type).isEqualTo(NON_ASSOCIATION_EVENT)
    }
    with(visitNotifications[1]) {
      Assertions.assertThat(visit.reference).isEqualTo(visitPastSecondary.reference)
      Assertions.assertThat(reference).isNotNull()
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
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    visitNotificationEventHelper.create(visit = visitPrimary, notificationEventType = NON_ASSOCIATION_EVENT)
    visitNotificationEventHelper.create(visit = visitSecondary, notificationEventType = NON_ASSOCIATION_EVENT)

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
  }

  @Test
  fun `when prisoners have current non association then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationForPrisonApi()

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    visitNotificationEventHelper.create(visit = visitPrimary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitSecondary.reference)))
    visitNotificationEventHelper.create(visit = visitSecondary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitPrimary.reference)))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
  }

  @Test
  fun `when prisoners are from a prison that is not supported then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()
    prisonOffenderSearchMockServer.stubGetPrisonerByString(primaryPrisonerId, "AAA", IncentiveLevel.ENHANCED)

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    visitNotificationEventHelper.create(visit = visitPrimary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitSecondary.reference)))
    visitNotificationEventHelper.create(visit = visitSecondary, notificationEventType = NON_ASSOCIATION_EVENT, notificationAttributes = mapOf(Pair(PAIRED_VISIT, visitPrimary.reference)))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
  }
}
