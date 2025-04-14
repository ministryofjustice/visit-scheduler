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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_EVENTS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType.VISITOR_ID
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType.VISITOR_RESTRICTION
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.VISITOR_RESTRICTION_UPSERTED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventAttributeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetVisitNotificationEvents
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEventAttribute
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("GET $VISIT_NOTIFICATION_EVENTS")
class GetVisitNotificationEventsTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification events requested by booking reference then all appropriate events are returned`() {
    // Given

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

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callGetVisitNotificationEvents(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notifications = this.getVisitNotificationEvents(responseSpec)
    Assertions.assertThat(notifications.size).isEqualTo(2)
    assertNotificationEvent(notifications[0], NON_ASSOCIATION_EVENT, emptyList())
    assertNotificationEvent(notifications[1], PRISONER_RESTRICTION_CHANGE_EVENT, emptyList())
  }

  @Test
  fun `when notification events includes additional data then all additional data for the notification event is returned`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val notification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, VISITOR_RESTRICTION_UPSERTED_EVENT))
    val eventAttribute1 = VisitNotificationEventAttribute(
      attributeName = VISITOR_ID,
      attributeValue = "10001",
      visitNotificationEventId = notification.id,
      visitNotificationEvent = notification,
    )

    val eventAttribute2 = VisitNotificationEventAttribute(
      attributeName = VISITOR_RESTRICTION,
      attributeValue = "BAN",
      visitNotificationEventId = notification.id,
      visitNotificationEvent = notification,
    )

    notification.visitNotificationEventAttributes.addAll(listOf(eventAttribute1, eventAttribute2))
    testVisitNotificationEventRepository.saveAndFlush(notification)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callGetVisitNotificationEvents(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notifications = this.getVisitNotificationEvents(responseSpec)
    Assertions.assertThat(notifications.size).isEqualTo(2)
    val eventAttributes = listOf(
      VisitNotificationEventAttributeDto(VISITOR_ID, "10001"),
      VisitNotificationEventAttributeDto(VISITOR_RESTRICTION, "BAN"),
    )
    assertNotificationEvent(notifications[0], VISITOR_RESTRICTION_UPSERTED_EVENT, eventAttributes)
    assertNotificationEvent(notifications[1], PRISONER_RESTRICTION_CHANGE_EVENT, emptyList())
  }

  @Test
  fun `when no notification events exist then an empty list is returned`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    // no notifications exist for the visit

    // When
    val responseSpec = callGetVisitNotificationEvents(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notifications = this.getVisitNotificationEvents(responseSpec)
    Assertions.assertThat(notifications.size).isEqualTo(0)
  }

  @Test
  fun `when no role specified then access forbidden status is returned`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val bookingReference = "AA-11-22"

    // When
    val responseSpec = callGetVisitNotificationEvents(webTestClient, bookingReference, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `when no token passed then unauthorized status is returned`() {
    // Given
    val bookingReference = "AA-11-22"

    // When
    val responseSpec = webTestClient.get().uri(VISIT_NOTIFICATION_EVENTS.replace("{reference}", bookingReference)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun assertNotificationEvent(notification: VisitNotificationEventDto, type: NotificationEventType, additionalData: List<VisitNotificationEventAttributeDto>) {
    Assertions.assertThat(notification.type).isEqualTo(type)
    Assertions.assertThat(notification.notificationEventReference).isNotNull()
    Assertions.assertThat(notification.createdDateTime).isNotNull()
    Assertions.assertThat(notification.additionalData).isEqualTo(additionalData)
  }
}
