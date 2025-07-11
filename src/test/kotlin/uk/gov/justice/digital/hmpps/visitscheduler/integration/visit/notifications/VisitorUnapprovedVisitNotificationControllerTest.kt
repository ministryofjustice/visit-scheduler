package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.PrisonerContactRegistryClient
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_VISITOR_UNAPPROVED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.PrisonerContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorApprovedUnapprovedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatVisitorUnapproved
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_VISITOR_UNAPPROVED_PATH")
class VisitorUnapprovedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @MockitoSpyBean
  private lateinit var prisonerContactRegistryClientSpy: PrisonerContactRegistryClient

  val prisonerId = "A1234AA"
  val visitorId = "4427942"
  val prisonCode = "ABC"

  lateinit var prison1: Prison
  lateinit var sessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visitor has been unapproved then future booked visits are flagged and saved`() {
    // Given
    val currentApprovedPrisonerContacts = emptyList<PrisonerContactDto>()
    prisonerContactRegistryMockServer.stubGetPrisonerApprovedSocialContacts(prisonerId, withAddress = false, currentApprovedPrisonerContacts)

    val notificationDto = VisitorApprovedUnapprovedNotificationDto(
      visitorId = visitorId,
      prisonerNumber = prisonerId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
    val responseSpec = callNotifyVSiPThatVisitorUnapproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.VISITOR_UNAPPROVED_EVENT)

    verify(prisonerContactRegistryClientSpy, times(1)).getPrisonersApprovedSocialContacts(prisonerId, withAddress = false)
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(1)
    assertThat(visitNotifications[0].visitNotificationEventAttributes[0].attributeName).isEqualTo(NotificationEventAttributeType.VISITOR_ID)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_UNAPPROVED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_UNAPPROVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when visitor has been unapproved but the visitor id is still an approved SOCIAL contact on prison contact registry then future booked visits are not flagged`() {
    // Given
    val currentApprovedPrisonerContacts = listOf(PrisonerContactDto(visitorId.toLong()))
    prisonerContactRegistryMockServer.stubGetPrisonerApprovedSocialContacts(prisonerId, withAddress = false, currentApprovedPrisonerContacts)

    val notificationDto = VisitorApprovedUnapprovedNotificationDto(
      visitorId = visitorId,
      prisonerNumber = prisonerId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
    val responseSpec = callNotifyVSiPThatVisitorUnapproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    verify(prisonerContactRegistryClientSpy, times(1)).getPrisonersApprovedSocialContacts(prisonerId, withAddress = false)

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_UNAPPROVED_EVENT)
    assertThat(auditEvents).hasSize(0)
  }

  @Test
  fun `when no visits then no call is made to prison contact registry`() {
    // Given
    val currentApprovedPrisonerContacts = listOf(PrisonerContactDto(1001))
    prisonerContactRegistryMockServer.stubGetPrisonerApprovedSocialContacts(prisonerId, withAddress = false, currentApprovedPrisonerContacts)

    val notificationDto = VisitorApprovedUnapprovedNotificationDto(
      visitorId = visitorId,
      prisonerNumber = prisonerId,
    )
    // When
    val responseSpec = callNotifyVSiPThatVisitorUnapproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    verify(prisonerContactRegistryClientSpy, times(0)).getPrisonersApprovedSocialContacts(prisonerId, withAddress = false)

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_UNAPPROVED_EVENT)
    assertThat(auditEvents).hasSize(0)
  }

  @Test
  fun `when visitor has been unapproved but call to prisoner contact registry throws a NOT_FOUND error still any future booked visits are flagged and saved`() {
    // Given
    prisonerContactRegistryMockServer.stubGetPrisonerApprovedSocialContacts(prisonerId, withAddress = false, null, HttpStatus.NOT_FOUND)

    val notificationDto = VisitorApprovedUnapprovedNotificationDto(
      visitorId = visitorId,
      prisonerNumber = prisonerId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
    val responseSpec = callNotifyVSiPThatVisitorUnapproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.VISITOR_UNAPPROVED_EVENT)

    verify(prisonerContactRegistryClientSpy, times(1)).getPrisonersApprovedSocialContacts(prisonerId, withAddress = false)
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(1)
    assertThat(visitNotifications[0].visitNotificationEventAttributes[0].attributeName).isEqualTo(NotificationEventAttributeType.VISITOR_ID)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_UNAPPROVED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_UNAPPROVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when visitor has been unapproved but call to prisoner contact registry throws a INTERNAL_SERVER_ERROR still any future booked visits are flagged and saved`() {
    // Given
    prisonerContactRegistryMockServer.stubGetPrisonerApprovedSocialContacts(prisonerId, withAddress = false, null, HttpStatus.INTERNAL_SERVER_ERROR)

    val notificationDto = VisitorApprovedUnapprovedNotificationDto(
      visitorId = visitorId,
      prisonerNumber = prisonerId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      visitSubStatus = VisitSubStatus.CANCELLED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
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
    val responseSpec = callNotifyVSiPThatVisitorUnapproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.VISITOR_UNAPPROVED_EVENT)

    verify(prisonerContactRegistryClientSpy, times(1)).getPrisonersApprovedSocialContacts(prisonerId, withAddress = false)
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(1)
    assertThat(visitNotifications[0].visitNotificationEventAttributes[0].attributeName).isEqualTo(NotificationEventAttributeType.VISITOR_ID)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.VISITOR_UNAPPROVED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visit1.reference)
      assertThat(applicationReference).isEqualTo(visit1.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visit1.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.VISITOR_UNAPPROVED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }
}
