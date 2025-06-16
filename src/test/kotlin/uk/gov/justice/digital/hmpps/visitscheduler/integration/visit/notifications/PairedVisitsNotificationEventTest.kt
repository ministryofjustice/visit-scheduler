package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.IgnoreVisitNotificationsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.PHONE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.CANCELLED_NON_ASSOCIATION_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.IGNORED_NON_ASSOCIATION_VISIT_NOTIFICATIONS_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.UPDATED_NON_ASSOCIATION_VISIT_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.IncentiveLevel
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NonAssociationDomainEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType.PAIRED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType.VISITOR_UNAPPROVED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.NonAssociationChangedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitNotificationEventAttributeDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callIgnoreVisitNotifications
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatNonAssociationHasChanged
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitUpdate
import uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.CancelVisitTest.Companion.CANCELLED_BY_USER
import uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.IgnoreVisitNotificationsTest.Companion.USER
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@DisplayName("tests for cancel / update / ignore a visit that has a paired visit notification event - e.g. non-association")
class PairedVisitsNotificationEventTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit
  val prisoner1 = "AA11BCC"
  val prisoner2 = "XX11YZZ"
  val prisonCode = "ABC"
  val contact = ContactDto(name = "John Smith", telephone = "01234567890", email = null)

  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var sessionTemplate2: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0))
    sessionTemplate2 = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0))
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisoner1, prisonCode, IncentiveLevel.ENHANCED)
  }

  /**
   * Scenario - a visit booked on same day for prisoner1 and prisoner2.
   * non-association added between prisoner1 and prisoner2.
   * both visits flagged as a pair.
   * prisoner1 visit cancelled
   * non-association notifications for prisoner2's visits are also deleted.
   * audit entry added for prisoner2's visit
   */
  @Test
  fun `when a visit and non-association visit are flagged and visit cancelled then non-association visit's associated event is also deleted and an audit entry is added`() {
    // Given
    val visitDate = LocalDate.now().with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek)).plusWeeks(1)
    val prisoner1Visit = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit)
    val prisoner2Visit = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit)

    // a non-association was added after the visit was created and a paired notification was added
    val nonAssociationCreatedNotification = NonAssociationChangedNotificationDto(NonAssociationDomainEventType.NON_ASSOCIATION_CREATED, prisoner1, prisoner2)
    var responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationCreatedNotification)
    responseSpec.expectStatus().isOk

    // ensure 2 notifications with the same reference were added
    var visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    // notification 1 - booking reference for prisoner1 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 2 - booking reference for prisoner2 and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit.reference)))

    visitNotificationEventHelper.create(prisoner2Visit, VISITOR_UNAPPROVED_EVENT)
    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(3)

    // when the first visit is cancelled the second visit's non association events are also removed
    val cancelVisitDto = CancelVisitDto(OutcomeDto(OutcomeStatus.PRISONER_CANCELLED, "Prisoner got covid"), CANCELLED_BY_USER, STAFF, PHONE)
    responseSpec = callCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, prisoner1Visit.reference, cancelVisitDto)
    responseSpec.expectStatus().isOk

    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(prisoner2Visit.reference)
    assertThat(visitNotifications[0].type).isEqualTo(VISITOR_UNAPPROVED_EVENT)

    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit.reference)
    assertEventAudit(eventAudit, CANCELLED_NON_ASSOCIATION_VISIT_EVENT, prisoner2Visit.reference, "Non-association's visit with reference - ${prisoner1Visit.reference} was cancelled.")
  }

  /**
   * Scenario - a visit booked on same day for prisoner1 and prisoner2.
   * non-association added between prisoner1 and prisoner2.
   * both visits flagged as a pair.
   * prisoner1 visit updated and moved to a different date
   * non-association notifications for prisoner2's visits are also deleted.
   * audit entry added for prisoner2's visit
   */
  @Test
  fun `when a visit and non-association visit are flagged and visit updated with new date then non-association visit's associated event is also deleted and an audit entry is added`() {
    // Given
    val visitDate = LocalDate.now().with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek)).plusWeeks(1)
    val prisoner1Visit = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit)
    val prisoner2Visit = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit)

    // a non-association was added after the visit was created and a paired notification was added
    val nonAssociationCreatedNotification = NonAssociationChangedNotificationDto(NonAssociationDomainEventType.NON_ASSOCIATION_CREATED, prisoner1, prisoner2)
    var responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationCreatedNotification)
    responseSpec.expectStatus().isOk

    // ensure 2 notifications with the same reference were added
    var visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications).hasSize(2)
    // notification 1 - booking reference for prisoner1 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 2 - booking reference for prisoner2 and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit.reference)))

    visitNotificationEventHelper.create(prisoner2Visit, VISITOR_UNAPPROVED_EVENT)
    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(3)

    // when the first visit is updated to a new date the second visit's non association events are also removed
    var application = applicationEntityHelper.create(prisoner1Visit, sessionTemplate = sessionTemplate1, newSlotDate = prisoner1Visit.sessionSlot.slotDate.plusWeeks(1))
    applicationEntityHelper.createContact(application, contact)
    application.visit = prisoner1Visit
    application.visitId = prisoner1Visit.id
    application = applicationEntityHelper.save(application)

    // When
    responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, application.reference)
    responseSpec.expectStatus().isOk

    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(prisoner2Visit.reference)
    assertThat(visitNotifications[0].type).isEqualTo(VISITOR_UNAPPROVED_EVENT)

    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit.reference)
    assertEventAudit(eventAudit, UPDATED_NON_ASSOCIATION_VISIT_EVENT, prisoner2Visit.reference, "Non-association's visit with reference - ${prisoner1Visit.reference} was updated.")
  }

  /**
   * Scenario - a visit booked on same day for prisoner1 and prisoner2.
   * non-association added between prisoner1 and prisoner2.
   * both visits flagged as a pair.
   * prisoner1's visit's notifications are ignored
   * non-association notifications for prisoner2's visits are also deleted.
   * audit entry added for prisoner2's visit
   */
  @Test
  fun `when a visit and non-association visit are flagged and visit notifications are ignored then non-association visit's associated event is also deleted and an audit entry is added`() {
    // Given
    val visitDate = LocalDate.now().with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek)).plusWeeks(1)
    val prisoner1Visit = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit)
    val prisoner2Visit = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit)

    // a non-association was added after the visit was created and a paired notification was added
    val nonAssociationCreatedNotification = NonAssociationChangedNotificationDto(NonAssociationDomainEventType.NON_ASSOCIATION_CREATED, prisoner1, prisoner2)
    var responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationCreatedNotification)
    responseSpec.expectStatus().isOk

    // ensure 2 notifications were added
    var visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    // notification 1 - booking reference for prisoner1 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 2 - booking reference for prisoner2 and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit.reference)))

    // when the first visit's notifications are ignored the second visit's non association events are also removed
    val ignoreVisitNotification = IgnoreVisitNotificationsDto("Can be ignored, to be managed by staff.", USER)

    // When
    responseSpec = callIgnoreVisitNotifications(webTestClient, roleVisitSchedulerHttpHeaders, prisoner1Visit.reference, ignoreVisitNotification)

    // Then
    responseSpec.expectStatus().isOk

    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)

    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit.reference)
    assertEventAudit(eventAudit, IGNORED_NON_ASSOCIATION_VISIT_NOTIFICATIONS_EVENT, prisoner2Visit.reference, "Non-association's visit with reference - ${prisoner1Visit.reference}'s notifications were ignored.")
  }

  /**
   * Scenario - a visit booked on same day for prisoner1 and 2 visits booked for prisoner2 (both on same day).
   * non-association added between prisoner1 and prisoner2.
   * both visits of prisoner2 are flagged as a pair of prisoner1's visit.
   * prisoner1's visit's notifications are cancelled
   * non-association notifications for both of prisoner2's visits are also deleted.
   * audit entry added for both of prisoner2's visit
   */
  @Test
  fun `when a visit and multiple non-association visits are flagged and visit cancelled then both non-association visit's associated event are deleted and an audit entry is added for both`() {
    // Given
    val visitDate = LocalDate.now().with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek)).plusWeeks(1)
    val prisoner1Visit = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit)
    val prisoner2Visit1 = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit1)
    val prisoner2Visit2 = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate2, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit2)

    // a non-association was added after the visit was created and a paired notification was added
    val nonAssociationCreatedNotification = NonAssociationChangedNotificationDto(NonAssociationDomainEventType.NON_ASSOCIATION_CREATED, prisoner1, prisoner2)
    var responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationCreatedNotification)
    responseSpec.expectStatus().isOk

    // ensure 3 notifications with the same reference were added
    var visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(4)

    // notification 1 - booking reference for prisoner1 and paired with prisoner2's visit1 on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit1.reference)))
    // notification 2 - booking reference for prisoner2 visit1 and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit.reference)))
    // notification 3 - booking reference for prisoner1 and paired with prisoner2's visit2 on same day
    assertNotificationEvent(visitNotifications[2], prisoner1Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit2.reference)))
    // notification 4 - booking reference for prisoner2 visit2 and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[3], prisoner2Visit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit.reference)))

    // when the first visit is cancelled the second visit's non association events are also removed
    val cancelVisitDto = CancelVisitDto(OutcomeDto(OutcomeStatus.PRISONER_CANCELLED, "Prisoner got covid"), CANCELLED_BY_USER, STAFF, PHONE)
    responseSpec = callCancelVisit(webTestClient, roleVisitSchedulerHttpHeaders, prisoner1Visit.reference, cancelVisitDto)
    responseSpec.expectStatus().isOk

    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    var eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit1.reference)
    assertEventAudit(eventAudit, CANCELLED_NON_ASSOCIATION_VISIT_EVENT, prisoner2Visit1.reference, "Non-association's visit with reference - ${prisoner1Visit.reference} was cancelled.")

    eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit1.reference)
    assertEventAudit(eventAudit, CANCELLED_NON_ASSOCIATION_VISIT_EVENT, prisoner2Visit1.reference, "Non-association's visit with reference - ${prisoner1Visit.reference} was cancelled.")
  }

  /**
   * Scenario - 2 visits booked on same day for prisoner1 and 1 visit booked for prisoner2.
   * non-association added between prisoner1 and prisoner2.
   * both visits of prisoner1 are flagged as a pair of prisoner2's visit.
   * only 1 of prisoner1's visit's notifications are ignored
   * non-association notifications paired with prisoner1's visits are ignored.
   * audit entry added for prisoner2's visit
   */
  @Test
  fun `when a prisoner has multiple visits on the day and single non-association visit - and 1 of the prisoner visit is ignored then non-association for only 1 visit are deleted and an audit entry is added for both`() {
    // Given
    val visitDate = LocalDate.now().with(TemporalAdjusters.next(sessionTemplate1.dayOfWeek)).plusWeeks(1)
    val prisoner1Visit1 = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit1)
    val prisoner1Visit2 = visitEntityHelper.create(prisonerId = prisoner1, slotDate = visitDate, sessionTemplate = sessionTemplate2, visitContact = contact)
    eventAuditEntityHelper.create(prisoner1Visit2)

    val prisoner2Visit = visitEntityHelper.create(prisonerId = prisoner2, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = contact)
    eventAuditEntityHelper.create(prisoner2Visit)

    // a non-association was added after the visit was created and a paired notification was added
    val nonAssociationCreatedNotification = NonAssociationChangedNotificationDto(NonAssociationDomainEventType.NON_ASSOCIATION_CREATED, prisoner1, prisoner2)
    var responseSpec = callNotifyVSiPThatNonAssociationHasChanged(webTestClient, roleVisitSchedulerHttpHeaders, nonAssociationCreatedNotification)
    responseSpec.expectStatus().isOk

    // ensure 2 notifications with the same reference were added
    var visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(4)

    // notification 1 - booking reference for prisoner1's visit 1 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit1.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 2 - booking reference for prisoner2 visit and paired with prisoner1's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit1.reference)))
    // notification 3 - booking reference for prisoner1's visit 2 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[2], prisoner1Visit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 4 - booking reference for prisoner2 visit and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[3], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit2.reference)))

    // when 1 of the prisoner 1's visit then the non-association visit's non association events with only that visit are removed
    val ignoreVisitNotification = IgnoreVisitNotificationsDto("Can be ignored, to be managed by staff.", USER)

    // When
    responseSpec = callIgnoreVisitNotifications(webTestClient, roleVisitSchedulerHttpHeaders, prisoner1Visit1.reference, ignoreVisitNotification)

    // Then
    responseSpec.expectStatus().isOk
    visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)

    // notification 3 - booking reference for prisoner1's visit 2 and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[0], prisoner1Visit2.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner2Visit.reference)))
    // notification 4 - booking reference for prisoner2 visit and paired with prisoner2's visit on same day
    assertNotificationEvent(visitNotifications[1], prisoner2Visit.reference, listOf(VisitNotificationEventAttributeDto(PAIRED_VISIT, prisoner1Visit2.reference)))

    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(prisoner2Visit.reference)
    assertEventAudit(eventAudit, IGNORED_NON_ASSOCIATION_VISIT_NOTIFICATIONS_EVENT, prisoner2Visit.reference, "Non-association's visit with reference - ${prisoner1Visit1.reference}'s notifications were ignored.")
  }

  private fun assertEventAudit(eventAudit: EventAudit, eventAuditType: EventAuditType, visitReference: String, text: String) {
    assertThat(eventAudit.type).isEqualTo(eventAuditType)
    assertThat(eventAudit.actionedBy.userType).isEqualTo(UserType.SYSTEM)
    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.NOT_APPLICABLE)
    assertThat(eventAudit.bookingReference).isEqualTo(visitReference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
    assertThat(eventAudit.applicationReference).isNull()
    assertThat(eventAudit.actionedBy.id).isNotNull()
    assertThat(eventAudit.text).isEqualTo(text)
  }

  private fun assertNotificationEvent(visitNotificationEvent: VisitNotificationEvent, expectedVisitReference: String, expectedNotificationEventAttributes: List<VisitNotificationEventAttributeDto>?) {
    assertThat(visitNotificationEvent.visit.reference).isEqualTo(expectedVisitReference)
    if (expectedNotificationEventAttributes != null) {
      assertThat(visitNotificationEvent.visitNotificationEventAttributes.size).isEqualTo(expectedNotificationEventAttributes.size)
      for (i in expectedNotificationEventAttributes.indices) {
        assertThat(visitNotificationEvent.visitNotificationEventAttributes[i].attributeName).isEqualTo(expectedNotificationEventAttributes[i].attributeName)
        assertThat(visitNotificationEvent.visitNotificationEventAttributes[i].attributeValue).isEqualTo(expectedNotificationEventAttributes[i].attributeValue)
      }
    } else {
      assertThat(visitNotificationEvent.visitNotificationEventAttributes).isNull()
    }
  }
}
