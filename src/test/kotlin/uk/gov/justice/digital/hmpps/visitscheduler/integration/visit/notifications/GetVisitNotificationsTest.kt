package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATIONS
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGetVisitNotificationTypes
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("GET $VISIT_NOTIFICATIONS")
class GetVisitNotificationsTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification types is requested by booking reference then all appropriate types are returned`() {
    // Given

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
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callGetVisitNotificationTypes(webTestClient, visitPrimary.reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notifications = this.getNotificationTypes(responseSpec)
    Assertions.assertThat(notifications.size).isEqualTo(2)
    Assertions.assertThat(notifications[0]).isEqualTo(NON_ASSOCIATION_EVENT)
    Assertions.assertThat(notifications[1]).isEqualTo(PRISONER_RESTRICTION_CHANGE_EVENT)
  }
}
