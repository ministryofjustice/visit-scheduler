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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType.NON_ASSOCIATION_DELETED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_DELETED")
class DeleteNonAssociationVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private val nonAssociationDomainEventType = NON_ASSOCIATION_DELETED

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"
  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
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

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(0)
    assertNonAssociationUnFlagEvent(visitPrimary.reference)
    assertNonAssociationUnFlagEvent(visitSecondary.reference)
    verify(telemetryClient, times(2)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoners have no current non association but notifications are in the past then notifications are not removed`() {
    // Given
    val nonAssociationChangedNotification = NonAssociationChangedNotificationDto(nonAssociationDomainEventType, primaryPrisonerId, secondaryPrisonerId)
    stubGetPrisonerNonAssociationEmpty()

    val visitPastPrimary = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitPastPrimary)

    val visitPastSecondary = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().minusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitPastSecondary)

    val pastVisitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPastPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPastSecondary.reference, NON_ASSOCIATION_EVENT, _reference = pastVisitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
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

    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), any())
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

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), any())
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

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)

    verifyNoInteractions(visitNotificationEventRepository)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), any())
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

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))

    // When
    val responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationChangedNotification)

    // Then
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    Assertions.assertThat(visitNotifications).hasSize(2)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), any())
  }

  fun assertNonAssociationUnFlagEvent(
    visitReference: String,
  ) {
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitReference)
        Assertions.assertThat(it["reviewTypes"]).isEqualTo(NON_ASSOCIATION_EVENT.reviewType)
        Assertions.assertThat(it["reason"]).isEqualTo(UnFlagEventReason.NON_ASSOCIATION_REMOVED.desc)
      },
      isNull(),
    )
  }
}
