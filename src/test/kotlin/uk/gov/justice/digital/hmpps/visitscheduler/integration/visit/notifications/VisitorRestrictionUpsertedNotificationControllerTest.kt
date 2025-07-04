package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_VISITOR_RESTRICTION_UPSERTED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatVisitorRestrictionUpserted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_VISITOR_RESTRICTION_UPSERTED_PATH")
class VisitorRestrictionUpsertedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "A1234AA"
  val visitorId = "4427942"
  val visitorRestrictionId = "123"
  val prisonCode = "ABC"
  val otherPrisonCode = "DEF"

  lateinit var prison1: Prison
  lateinit var prison2: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var otherPrisonSessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    prison2 = prisonEntityHelper.create(prisonCode = otherPrisonCode)

    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    otherPrisonSessionTemplate = sessionTemplateEntityHelper.create(prison = prison2)

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visitor has restriction upserted then valid visits in all prisons flagged and saved`() {
    // Given
    val notificationDto = VisitorRestrictionUpsertedNotificationDto(
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(1),
      restrictionType = VisitorSupportedRestrictionType.BAN.name,
      restrictionId = visitorRestrictionId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = otherPrisonSessionTemplate,
    )

    visit2.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit2.id,
        visit = visit2,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit2)
    eventAuditEntityHelper.create(visit2)

    val visit3 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = otherPrisonSessionTemplate,
    )

    visit3.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit3.id,
        visit = visit3,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit3)
    eventAuditEntityHelper.create(visit3)

    // When
    val responseSpec = callNotifyVSiPThatVisitorRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.VISITOR_RESTRICTION_UPSERTED_EVENT)

    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(3)
    assertThat(visitNotifications[0].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId),
      )

    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes.size).isEqualTo(3)
    assertThat(visitNotifications[1].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId),
      )

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
    with(auditEvents[1]) {
      assertThat(bookingReference).isEqualTo(visit2.reference)
      assertThat(applicationReference).isEqualTo(visit2.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit2.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when visitor is given a restriction with a date in the past then no visits are flagged or saved`() {
    // Given
    val notificationDto = VisitorRestrictionUpsertedNotificationDto(
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().minusDays(1),
      restrictionType = VisitorSupportedRestrictionType.CLOSED.name,
      restrictionId = visitorRestrictionId,
    )

    // When
    val responseSpec = callNotifyVSiPThatVisitorRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when visitor is given a restriction with expiry date then no visits after expiry are flagged or saved`() {
    // Given
    val notificationDto = VisitorRestrictionUpsertedNotificationDto(
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().plusDays(5),
      restrictionType = VisitorSupportedRestrictionType.CLOSED.name,
      restrictionId = visitorRestrictionId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    val visit2 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(10),
      visitStatus = BOOKED,
      sessionTemplate = otherPrisonSessionTemplate,
    )

    visit2.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit2.id,
        visit = visit2,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit2)
    eventAuditEntityHelper.create(visit2)

    // When
    val responseSpec = callNotifyVSiPThatVisitorRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.VISITOR_RESTRICTION_UPSERTED_EVENT)

    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when multiple visitor restrictions with the same restriction type multiple notification events are added and not rejected as duplicates `() {
    // Given
    val notificationDto1 = VisitorRestrictionUpsertedNotificationDto(
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().plusDays(5),
      restrictionType = VisitorSupportedRestrictionType.CLOSED.name,
      restrictionId = visitorRestrictionId,
    )

    val notificationDto2 = VisitorRestrictionUpsertedNotificationDto(
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().plusDays(5),
      restrictionType = VisitorSupportedRestrictionType.PREINF.name,
      restrictionId = visitorRestrictionId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId.toLong(),
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    // When
    var responseSpec = callNotifyVSiPThatVisitorRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto1)
    responseSpec.expectStatus().isOk
    responseSpec = callNotifyVSiPThatVisitorRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto2)
    responseSpec.expectStatus().isOk

    // Then
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.CLOSED.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId),
      )

    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.PREINF.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId),
      )

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
    with(auditEvents[1]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }
}
