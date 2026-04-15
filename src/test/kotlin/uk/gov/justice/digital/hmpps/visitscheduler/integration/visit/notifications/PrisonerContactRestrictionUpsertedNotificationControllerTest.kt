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
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PRISONER_CONTACT_RESTRICTION_UPSERTED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.RestrictionDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonerContactRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPrisonerContactRestrictionUpserted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PRISONER_CONTACT_RESTRICTION_UPSERTED_PATH")
class PrisonerContactRestrictionUpsertedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "A1234AA"
  val visitorId = 4427942L
  val prisonerContactId = 13L
  val visitorRestrictionId = 123L
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
  fun `when a prisoner contact restriction is created then valid visits in all prisons flagged and saved`() {
    // Given
    val notificationDto = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId,
    )

    val contactRestrictions = listOf(
      RestrictionDto(contactRestrictionId = visitorRestrictionId, contactId = visitorId, restrictionType = VisitorSupportedRestrictionType.BAN.name, startDate = LocalDate.now()),
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId,
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
        nomisPersonId = visitorId,
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
        nomisPersonId = visitorId,
        visitId = visit3.id,
        visit = visit3,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit3)
    eventAuditEntityHelper.create(visit3)

    // When
    prisonerContactRegistryMockServer.stubGetPrisonerContactRelationshipDetailsWithRestrictions(prisonerId, visitorId, prisonerContactId, PrisonerContactDto(visitorId, contactRestrictions))
    val responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT)

    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(3)
    assertThat(visitNotifications[0].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId.toString()),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId.toString()),
      )

    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes.size).isEqualTo(3)
    assertThat(visitNotifications[1].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId.toString()),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId.toString()),
      )

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
    with(auditEvents[1]) {
      assertThat(bookingReference).isEqualTo(visit2.reference)
      assertThat(applicationReference).isEqualTo(visit2.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit2.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when a prisoner contact restriction is created but cannot be found in contact registry API then no visits are flagged or saved`() {
    // Given

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId,
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    val notificationDto = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId,
    )

    // When
    prisonerContactRegistryMockServer.stubGetPrisonerContactRelationshipDetailsWithRestrictions(prisonerId, visitorId, prisonerContactId, null, HttpStatus.NOT_FOUND)
    val responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when a prisoner contact restriction is created but no visits exist for that visitorId then no visits are flagged or saved`() {
    // Given
    val notificationDto = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = 999L,
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    val contactRestrictions = listOf(
      RestrictionDto(contactRestrictionId = visitorRestrictionId, contactId = visitorId, restrictionType = VisitorSupportedRestrictionType.CLOSED.name, startDate = LocalDate.now()),
    )

    // When
    prisonerContactRegistryMockServer.stubGetPrisonerContactRelationshipDetailsWithRestrictions(prisonerId, visitorId, prisonerContactId, PrisonerContactDto(visitorId, contactRestrictions))
    val responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when a prisoner contact restriction is created with expiry date in the past then no visits after expiry are flagged or saved`() {
    // Given
    val notificationDto = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId,
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
        nomisPersonId = visitorId,
        visitId = visit2.id,
        visit = visit2,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit2)
    eventAuditEntityHelper.create(visit2)

    val contactRestrictions = listOf(
      RestrictionDto(contactRestrictionId = visitorRestrictionId, contactId = visitorId, restrictionType = VisitorSupportedRestrictionType.BAN.name, startDate = LocalDate.now().minusDays(1), expiryDate = LocalDate.now().minusDays(1)),
    )

    // When
    prisonerContactRegistryMockServer.stubGetPrisonerContactRelationshipDetailsWithRestrictions(prisonerId, visitorId, prisonerContactId, PrisonerContactDto(visitorId, contactRestrictions))
    val responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when multiple visitor restrictions with the same restriction type multiple notification events are added and not rejected as duplicates`() {
    // Given
    val visitorRestrictionId2 = 200L
    val notificationDto1 = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId,
    )

    val notificationDto2 = PrisonerContactRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      contactId = visitorId,
      prisonerContactId = prisonerContactId,
      restrictionId = visitorRestrictionId2,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate1,
    )

    visit1.visitors.add(
      VisitVisitor(
        nomisPersonId = visitorId,
        visitId = visit1.id,
        visit = visit1,
        visitContact = true,
      ),
    )

    visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    val contactRestrictions = listOf(
      RestrictionDto(contactRestrictionId = visitorRestrictionId, contactId = visitorId, restrictionType = VisitorSupportedRestrictionType.BAN.name, startDate = LocalDate.now()),
      RestrictionDto(contactRestrictionId = visitorRestrictionId2, contactId = visitorId, restrictionType = VisitorSupportedRestrictionType.BAN.name, startDate = LocalDate.now()),
    )

    // When
    prisonerContactRegistryMockServer.stubGetPrisonerContactRelationshipDetailsWithRestrictions(prisonerId, visitorId, prisonerContactId, PrisonerContactDto(visitorId, contactRestrictions))
    var responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto1)
    responseSpec.expectStatus().isOk
    responseSpec = callNotifyVSiPThatPrisonerContactRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto2)
    responseSpec.expectStatus().isOk

    // Then
    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId.toString()),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId.toString()),
      )

    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes)
      .extracting({ it.attributeName }, { it.attributeValue })
      .containsExactlyInAnyOrder(
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION, VisitorSupportedRestrictionType.BAN.name),
        tuple(NotificationEventAttributeType.VISITOR_RESTRICTION_ID, visitorRestrictionId2.toString()),
        tuple(NotificationEventAttributeType.VISITOR_ID, visitorId.toString()),
      )

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(2)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
    with(auditEvents[1]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }
}
