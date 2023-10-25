package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_COUNT_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCountVisitNotification
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_DELETED")
class CountVisitNotificationTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification count is requested for all prisons`() {
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

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = "TST",
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_PATH, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)
  }

  @Test
  fun `when notification count is requested for a prisons`() {
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

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      visitStart = LocalDateTime.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = "TST",
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH.replace("{prisonCode}", prisonCode), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(1)
  }
}