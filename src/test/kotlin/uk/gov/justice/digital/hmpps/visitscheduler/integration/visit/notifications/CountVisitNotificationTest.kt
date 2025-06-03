package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.NON_ASSOCIATION_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RECEIVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RELEASED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCountVisitNotification
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
@DisplayName("Get $VISIT_NOTIFICATION_COUNT_FOR_PRISON_PATH")
class CountVisitNotificationTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when notification count is requested for a prison, then correct count is returned`() {
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
    eventAuditEntityHelper.create(visitOther)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, prisonCode = sessionTemplateDefault.prison.code, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)
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
    eventAuditEntityHelper.create(visitOther)

    val visitNotification = testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitPrimary.reference, NON_ASSOCIATION_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitSecondary.reference, NON_ASSOCIATION_EVENT, _reference = visitNotification.reference))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visitOther.reference, PRISONER_RESTRICTION_CHANGE_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, prisonCode = sessionTemplateDefault.prison.code, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

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
    val responseSpec = callCountVisitNotification(webTestClient, prisonCode = sessionTemplateDefault.prison.code, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

    // Then count returned is 0
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
    eventAuditEntityHelper.create(futureVisitTomorrow)

    val pastVisitYesterday = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate2.prison.code,
      sessionTemplate = sessionTemplate2,
    )
    eventAuditEntityHelper.create(pastVisitYesterday)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitToday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitYesterday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisitToday.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisitTomorrow.reference, PRISONER_RELEASED_EVENT))

    // When
    val responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)
  }

  @Test
  fun `when notification count is requested for a prison with notification types specified then the correct count is returned`() {
    // Given
    val futureSessionStartTime = LocalTime.MAX
    val prisonCode = "ABC"

    val sessionTemplate1 = sessionTemplateEntityHelper.create(startTime = futureSessionStartTime, prisonCode = prisonCode)

    val futureVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now(),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisit1)

    val futureVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisit2)

    val futureVisit3 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisit3)

    val futureVisit4 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(futureVisit4)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit1.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit2.reference, PRISONER_ALERTS_UPDATED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit3.reference, PRISON_VISITS_BLOCKED_FOR_DATE))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit4.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit4.reference, PRISONER_RECEIVED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(futureVisit4.reference, PRISONER_ALERTS_UPDATED_EVENT))

    // When notification count sought for PRISONER_RELEASED_EVENT
    var responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_RELEASED_EVENT), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    var notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)

    // When notification count sought for PRISONER_ALERTS_UPDATED_EVENT
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_ALERTS_UPDATED_EVENT), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(2)

    // When notification count sought for PRISON_VISITS_BLOCKED_FOR_DATE
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISON_VISITS_BLOCKED_FOR_DATE), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(1)

    // When notification count sought for PRISONER_RELEASED_EVENT and PRISON_VISITS_BLOCKED_FOR_DATE
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_RELEASED_EVENT, PRISON_VISITS_BLOCKED_FOR_DATE), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(3)

    // When  notification count sought for all event types
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(4)
  }

  @Test
  fun `when notification count is requested for a prison with  notification types specified but no future visits exist hen the correct count is returned`() {
    // Given
    val sessionStartTime = LocalTime.MAX
    val prisonCode = "ABC"

    val sessionTemplate1 = sessionTemplateEntityHelper.create(startTime = sessionStartTime, prisonCode = prisonCode)

    // all visits are in the past
    val pastVisit1 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(1),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(pastVisit1)

    val pastVisit2 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().minusDays(2),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(pastVisit2)

    val pastVisit3 = visitEntityHelper.create(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().minusDays(3),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(pastVisit3)

    val pastVisit4 = visitEntityHelper.create(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().minusDays(4),
      visitStatus = BOOKED,
      prisonCode = sessionTemplate1.prison.code,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(pastVisit4)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit1.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit2.reference, PRISONER_ALERTS_UPDATED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit3.reference, PRISON_VISITS_BLOCKED_FOR_DATE))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit4.reference, PRISONER_RELEASED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit4.reference, PRISONER_RECEIVED_EVENT))
    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(pastVisit4.reference, PRISONER_ALERTS_UPDATED_EVENT))

    // When notification count sought for PRISONER_RELEASED_EVENT
    var responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_RELEASED_EVENT), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    var notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)

    // When notification count sought for PRISONER_ALERTS_UPDATED_EVENT
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_ALERTS_UPDATED_EVENT), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)

    // When notification count sought for PRISON_VISITS_BLOCKED_FOR_DATE
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISON_VISITS_BLOCKED_FOR_DATE), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)

    // When notification count sought for PRISONER_RELEASED_EVENT and PRISON_VISITS_BLOCKED_FOR_DATE
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = listOf(PRISONER_RELEASED_EVENT, PRISON_VISITS_BLOCKED_FOR_DATE), roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)

    // When  notification count sought for all event types
    responseSpec = callCountVisitNotification(webTestClient, prisonCode = prisonCode, notificationEventTypes = null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    notificationCount = this.getNotificationCountDto(responseSpec)
    Assertions.assertThat(notificationCount.count).isEqualTo(0)
  }
}
