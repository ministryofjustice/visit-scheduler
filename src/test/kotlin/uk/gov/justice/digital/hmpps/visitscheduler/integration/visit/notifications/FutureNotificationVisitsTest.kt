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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.FUTURE_NOTIFICATION_VISITS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.audit.ActionedByDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CHANGING_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.UPDATED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET $FUTURE_NOTIFICATION_VISITS")
class FutureNotificationVisitsTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

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
  }

  @Test
  fun `when future visits exist with and without notifications for a prison then future visits with notifications are returned`() {
    // Given
    val pastVisitWithNotification = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    visitNotificationEventHelper.create(
      visit = pastVisitWithNotification,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    val futureVisitWithNotification1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitWithNotification1, type = BOOKED_VISIT, actionedByValue = "user1")
    eventAuditEntityHelper.create(futureVisitWithNotification1, type = UPDATED_VISIT, actionedByValue = "IUpdatedIT")
    eventAuditEntityHelper.create(
      futureVisitWithNotification1,
      type = CHANGING_VISIT,
      actionedByValue = "IChangeSomething",
    )
    val visit1Notification1 = visitNotificationEventHelper.create(
      visit = futureVisitWithNotification1,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    val futureVisitWithNotification2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    val visit2Notification1 = visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )
    val visit2Notification2 = visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = PRISONER_RECEIVED_EVENT,
    )
    val visit2Notification3 = visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = PRISONER_RESTRICTION_CHANGE_EVENT,
    )

    // this visit has no notifications so should not be returned
    val visitWithoutNotification = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitWithoutNotification)

    // When
    val responseSpec = callFutureNotificationVisits(webTestClient, prisonCode, null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitsWithNotifications = getVisitNotificationsDtoList(responseSpec)
    Assertions.assertThat(visitsWithNotifications).hasSize(2)
    assertVisitNotificationsDto(
      visitsWithNotifications[0],
      visit = futureVisitWithNotification1,
      bookedBy = ActionedByDto(bookerReference = null, userName = "user1", UserType.STAFF),
      notifications = listOf(VisitNotificationEventDto(visit1Notification1)),
    )

    assertVisitNotificationsDto(
      visitsWithNotifications[1],
      visit = futureVisitWithNotification2,
      bookedBy = ActionedByDto(bookerReference = null, userName = null, UserType.SYSTEM),
      notifications = listOf(
        VisitNotificationEventDto(visit2Notification1),
        VisitNotificationEventDto(visit2Notification2),
        VisitNotificationEventDto(visit2Notification3),
      ),
    )
  }

  @Test
  fun `when future visits exist with and without notifications for a prison by notification types then future visits with notifications by notification types are returned`() {
    // Given
    val pastVisitWithNotification = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    visitNotificationEventHelper.create(
      visit = pastVisitWithNotification,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    val futureVisitWithNotification1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitWithNotification1, type = BOOKED_VISIT, actionedByValue = "user1")
    eventAuditEntityHelper.create(futureVisitWithNotification1, type = UPDATED_VISIT, actionedByValue = "IUpdatedIT")
    eventAuditEntityHelper.create(
      futureVisitWithNotification1,
      type = CHANGING_VISIT,
      actionedByValue = "IChangeSomething",
    )

    visitNotificationEventHelper.create(
      visit = futureVisitWithNotification1,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    val futureVisitWithNotification2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = PRISONER_RECEIVED_EVENT,
    )

    val visit2Notification3 = visitNotificationEventHelper.create(
      visit = futureVisitWithNotification2,
      notificationEventType = PRISONER_RESTRICTION_CHANGE_EVENT,
    )

    // this visit has no notifications so should not be returned
    val visitWithoutNotification = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(visitWithoutNotification)

    // When
    val responseSpec = callFutureNotificationVisits(webTestClient, prisonCode, listOf(PRISONER_RESTRICTION_CHANGE_EVENT), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitsWithNotifications = getVisitNotificationsDtoList(responseSpec)
    Assertions.assertThat(visitsWithNotifications).hasSize(1)

    assertVisitNotificationsDto(
      visitsWithNotifications[0],
      visit = futureVisitWithNotification2,
      bookedBy = ActionedByDto(bookerReference = null, userName = null, UserType.SYSTEM),
      notifications = listOf(
        VisitNotificationEventDto(visit2Notification3),
      ),
    )
  }

  @Test
  fun `when future visits exist without notifications for a prison then future visits are not returned`() {
    // Given
    val pastVisitWithNotification = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )
    visitNotificationEventHelper.create(
      visit = pastVisitWithNotification,
      notificationEventType = NON_ASSOCIATION_EVENT,
    )

    // future visit without notifications
    createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // future visit without notifications
    createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    // When
    val responseSpec = callFutureNotificationVisits(webTestClient, prisonCode, types = null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val visitsWithNotifications = getVisitNotificationsDtoList(responseSpec)
    Assertions.assertThat(visitsWithNotifications).hasSize(0)
  }

  @Test
  fun `when invalid type passed then a BAD_REQUEST error is returned`() {
    // Given
    val type = "XYZ"
    val url = FUTURE_NOTIFICATION_VISITS.replace("{prisonCode}", prisonCode) + "?types=$type"
    // When
    val responseSpec = webTestClient.get().uri(url)
      .headers(roleVisitSchedulerHttpHeaders)
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when no role specified then access forbidden status is returned`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callFutureNotificationVisits(webTestClient, prisonCode, null, authHttpHeaders)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `when no token passed then unauthorized status is returned`() {
    // Given

    // When
    val responseSpec = webTestClient.get().uri(FUTURE_NOTIFICATION_VISITS.replace("{prisonCode}", prisonCode)).exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun assertVisitNotificationsDto(
    visitNotificationsDto: VisitNotificationsDto,
    visit: Visit,
    bookedBy: ActionedByDto,
    notifications: List<VisitNotificationEventDto>,
  ) {
    Assertions.assertThat(visitNotificationsDto.visitReference).isEqualTo(visit.reference)
    Assertions.assertThat(visitNotificationsDto.prisonerNumber).isEqualTo(visit.prisonerId)
    Assertions.assertThat(visitNotificationsDto.bookedBy).isEqualTo(bookedBy)
    Assertions.assertThat(visitNotificationsDto.visitDate).isEqualTo(visit.sessionSlot.slotDate)
    Assertions.assertThat(visitNotificationsDto.notifications).containsAll(notifications)
  }

  fun getVisitNotificationsDtoList(responseSpec: ResponseSpec): Array<VisitNotificationsDto> = objectMapper.readValue(
    responseSpec.expectBody().returnResult().responseBody,
    Array<VisitNotificationsDto>::class.java,
  )

  fun callFutureNotificationVisits(
    webTestClient: WebTestClient,
    prisonCode: String,
    types: List<NotificationEventType>?,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    var url = FUTURE_NOTIFICATION_VISITS.replace("{prisonCode}", prisonCode)
    types?.let {
      url += "?types=${types.joinToString(",")}"
    }

    return webTestClient.get().uri(url)
      .headers(authHttpHeaders)
      .exchange()
  }
}
