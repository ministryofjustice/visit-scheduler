package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PERSON_RESTRICTION_UPSERTED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionUpsertedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPersonRestrictionUpserted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PERSON_RESTRICTION_UPSERTED_PATH")
class PersonRestrictionUpsertedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "A1234AA"
  val visitorId = "4427942"
  val prisonCode = "ABC"

  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate
  lateinit var otherSessionTemplate: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)

    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    otherSessionTemplate = sessionTemplateEntityHelper.create(prison = prison1)

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visitor has restriction upserted then visits at a given prison flagged and saved`() {
    // Given
    val notificationDto = PersonRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(1),
      restrictionType = VisitorSupportedRestrictionType.BAN.name,
      restrictionId = "123",
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
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
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      sessionTemplate = otherSessionTemplate,
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
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = CANCELLED,
      sessionTemplate = otherSessionTemplate,
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
    val responseSpec = callNotifyVSiPThatPersonRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1, visit2), NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT)

    verify(visitNotificationEventRepository, times(2)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(2)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(3)
    assertThat(visitNotifications[1].visit.reference).isEqualTo(visit2.reference)
    assertThat(visitNotifications[1].visitNotificationEventAttributes.size).isEqualTo(3)

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
  fun `when visitor is given an unsupported restriction then no visits are flagged or saved`() {
    // Given
    val notificationDto = PersonRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(1),
      restrictionType = "UNSUPPORTED",
      restrictionId = "123",
    )

    // When
    val responseSpec = callNotifyVSiPThatPersonRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when visitor is given a restriction with a date in the past then no visits are flagged or saved`() {
    // Given
    val notificationDto = PersonRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().minusDays(1),
      restrictionType = VisitorSupportedRestrictionType.CLOSED.name,
      restrictionId = "123",
    )

    // When
    val responseSpec = callNotifyVSiPThatPersonRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    verify(telemetryClient, times(0)).trackEvent(eq("flagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when visitor is given a restriction with expiry date then no visits are flagged or saved after expiry`() {
    // Given
    val notificationDto = PersonRestrictionUpsertedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now().minusDays(2),
      validToDate = LocalDate.now().plusDays(5),
      restrictionType = VisitorSupportedRestrictionType.CLOSED.name,
      restrictionId = "123",
    )

    val visit1 = createApplicationAndVisit(
      prisonerId = notificationDto.prisonerNumber,
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
      prisonerId = notificationDto.prisonerNumber,
      slotDate = LocalDate.now().plusDays(10),
      visitStatus = BOOKED,
      sessionTemplate = otherSessionTemplate,
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
    val responseSpec = callNotifyVSiPThatPersonRestrictionUpserted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visit1), NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT)

    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit1.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.PERSON_RESTRICTION_UPSERTED_EVENT)
    assertThat(auditEvents).hasSize(1)
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
  }
}
