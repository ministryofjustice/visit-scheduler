package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_IGNORE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callIgnoreVisitNotifications
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getIgnoreVisitNotificationsUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository

@DisplayName("Put $VISIT_NOTIFICATION_IGNORE")
class IgnoreVisitNotificationsTest : IntegrationTestBase() {
  @Autowired
  protected lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  companion object {
    const val USER = "test_user"
  }

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var visitNotificationEventRepository: VisitNotificationEventRepository

  @Test
  fun `when ignore visit notifications raised for existing visit then all existing notifications are deleted`() {
    // Given - visit with 3 notification events
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.NON_ASSOCIATION_EVENT)
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.PRISONER_RELEASED_EVENT)

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )
    val reference = visit.reference

    // When
    val responseSpec = callIgnoreVisitNotifications(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, ignoreVisitNotification)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val ignoredVisit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertIgnoredVisit(ignoredVisit, ignoreVisitNotification)
    assertUnFlagEvent(ignoredVisit, ignoreVisitNotification.reason)
  }

  @Test
  fun `when ignore visit notifications raised for existing visit without notifications then request is still successful`() {
    // Given - visit with no notification events
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )
    val reference = visit.reference

    // When
    val responseSpec = callIgnoreVisitNotifications(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, ignoreVisitNotification)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val ignoredVisit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertIgnoredVisit(ignoredVisit, ignoreVisitNotification.actionedBy, STAFF, ignoreVisitNotification.reason)

    // if there are no notifications deleteByBookingReference should not be called
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(visit.reference))
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when ignore visit notifications raised for cancelled visit then NOT_FOUND status is returned`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = CANCELLED, slotDate = startDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    val reference = visit.reference

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )

    // When
    val responseSpec = callIgnoreVisitNotifications(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, ignoreVisitNotification)

    // Then
    responseSpec.expectStatus().isNotFound

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  @Test
  fun `when ignore visit notifications raised for non existing visit then NOT_FOUND status is returned`() {
    // Given
    val reference = "12345"

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )

    // When
    val responseSpec = callIgnoreVisitNotifications(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, ignoreVisitNotification)

    // Then
    responseSpec.expectStatus().isNotFound

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  @Test
  fun `when ignore visit notifications raised without correct role then  access forbidden returned`() {
    // Given
    val reference = "12345"

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )

    // When
    val responseSpec = callIgnoreVisitNotifications(webTestClient, setAuthorisation(roles = listOf()), reference, ignoreVisitNotification)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  @Test
  fun `when ignore visit notifications raised without token then unauthorised status returned`() {
    // Given
    val reference = "12345"

    val ignoreVisitNotification = IgnoreVisitNotificationsDto(
      "Can be ignored, to be managed by staff.",
      USER,
    )

    // When
    val responseSpec = webTestClient.put().uri(getIgnoreVisitNotificationsUrl(reference))
      .body(
        BodyInserters.fromValue(
          ignoreVisitNotification,
        ),
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  fun assertIgnoredVisit(visit: VisitDto, ignoreVisitNotification: IgnoreVisitNotificationsDto, userType: UserType = STAFF) {
    assertHelper.assertIgnoredVisit(visit, ignoreVisitNotification.actionedBy, userType, ignoreVisitNotification.reason)
    Assertions.assertThat(visit.visitNotes.size).isEqualTo(0)

    val visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))

    Assertions.assertThat(visitNotifications.size).isEqualTo(0)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
    assertUnFlagEvent(visit, ignoreVisitNotification.reason)
  }

  fun assertUnFlagEvent(
    visit: VisitDto,
    userEnteredReason: String,
  ) {
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["reason"]).isEqualTo(UnFlagEventReason.IGNORE_VISIT_NOTIFICATIONS.desc)
        Assertions.assertThat(it["text"]).isEqualTo(userEnteredReason)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
