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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCountVisitNotification
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_NON_ASSOCIATION_CHANGE_PATH NON_ASSOCIATION_DELETED")
class CountVisitNotificationTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification count is requested for all prisons`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = startDate.plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = startDate.plusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val sessionTemplateTst = sessionTemplateEntityHelper.create(prisonCode = "TST")

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = startDate.plusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateTst.prison.code,
      sessionTemplate = sessionTemplateTst,
    )
    eventAuditEntityHelper.create(visitOther)

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
  fun `when no booked visits for notification count is zero for all prisons`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val sessionTemplateTst = sessionTemplateEntityHelper.create(prisonCode = "TST")

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateTst.prison.code,
      sessionTemplate = sessionTemplateTst,
    )
    eventAuditEntityHelper.create(visitOther)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_PATH, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)
  }

  @Test
  fun `when notification count is requested for a prison`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val sessionTemplateTst = sessionTemplateEntityHelper.create(prisonCode = "TST")

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateTst.prison.code,
      sessionTemplate = sessionTemplateTst,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH.replace("{prisonCode}", sessionTemplateDefault.prison.code), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(1)
  }

  @Test
  fun `when no booked visits for notification count is zero for a prison`() {
    // Given

    val visitPrimary = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitPrimary)

    val visitSecondary = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitOther = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visitSecondary)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH.replace("{prisonCode}", sessionTemplateDefault.prison.code), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)
  }

  @Test
  fun `when notification count is requested for a prison and none exist then a count of 0 is returned`() {
    // Given - visits exist but no visit notifications for a prison

    val visit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit)

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH.replace("{prisonCode}", sessionTemplateDefault.prison.code), roleVisitSchedulerHttpHeaders)

    // Then count returned is 0
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)
  }

  @Test
  fun `when notification count is requested for all prisons and none exist then a count of 0 is returned`() {
    // Given - visits exist but no notifications
    val visit = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplateDefault.prison.code,
      sessionTemplate = sessionTemplateDefault,
    )
    eventAuditEntityHelper.create(visit)

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_PATH, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)
  }

  @Test
  fun `when notification count is requested for a prison and visits exist then the correct count is returned`() {
    // Given
    val futureSessionStartTime = LocalTime.MAX
    val pastSessionStartTime = LocalTime.MIN
    val prisonCode = "ABC"

    val sessionTemplate1 = sessionTemplateEntityHelper.create(startTime = futureSessionStartTime, prisonCode = prisonCode)
    val sessionTemplate2 = sessionTemplateEntityHelper.create(startTime = pastSessionStartTime, prisonCode = prisonCode)

    val futureVisitToday = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitToday)

    val pastVisitToday = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate2.prison.code,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(pastVisitToday)

    val futureVisitTomorrow = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisitToday)

    val pastVisitYesterday = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate2.prison.code,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(pastVisitToday)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitToday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitYesterday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitToday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitTomorrow.reference, PRISONER_RELEASED_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH.replace("{prisonCode}", prisonCode), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)
  }
}
