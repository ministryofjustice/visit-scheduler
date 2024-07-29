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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_PERSON_RESTRICTION_DELETED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISONER_ALERTS_UPDATED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitorSupportedRestrictionType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonercontactregistry.VisitorActiveRestrictionsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PersonRestrictionDeletedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatPersonRestrictionDeleted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_PERSON_RESTRICTION_DELETED_PATH")
class PersonRestrictionDeletedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
  val visitorId = 4427942L
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
  fun `when prisoner has had a restriction removed and has associated visits flagged, then they are un-flagged`() {
    // Given
    prisonerContactRegistryMockServer.stubGetVisitorActiveRestrictions(prisonerId, visitorId, VisitorActiveRestrictionsDto(listOf("RANDOM")))

    val notificationDto = PersonRestrictionDeletedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now(),
      validToDate = null,
      restrictionType = "CLOSED",
    )

    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit, visitorId.toLong(), true)
    visitEntityHelper.save(visit)
    eventAuditEntityHelper.create(visit)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visit.reference, NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT))

    // When
    val responseSpec = callNotifyVSiPThatPersonRestrictionDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewType"]).isEqualTo(NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.VISITOR_RESTRICTION_REMOVED.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when prisoner has had a supported restriction removed and but still has other supported restrictions on profile then no visits are un-flagged`() {
    // Given
    prisonerContactRegistryMockServer.stubGetVisitorActiveRestrictions(prisonerId, visitorId, VisitorActiveRestrictionsDto(listOf(VisitorSupportedRestrictionType.CLOSED.name)))

    val notificationDto = PersonRestrictionDeletedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now(),
      validToDate = null,
      restrictionType = VisitorSupportedRestrictionType.BAN.name,
    )

    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      prisonCode = prisonCode,
      sessionTemplate = sessionTemplateDefault,
    )
    visitEntityHelper.createVisitor(visit, visitorId.toLong(), true)
    visitEntityHelper.save(visit)
    eventAuditEntityHelper.create(visit)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visit.reference, NotificationEventType.PERSON_RESTRICTION_UPSERTED_EVENT))

    // When
    val responseSpec = callNotifyVSiPThatPersonRestrictionDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(PRISONER_ALERTS_UPDATED_EVENT)).isEqualTo(0)
  }

  @Test
  fun `when prisoner has had a not supported restriction removed then no visits are un-flagged`() {
    // Given
    val notificationDto = PersonRestrictionDeletedNotificationDto(
      prisonerNumber = prisonerId,
      visitorId = visitorId,
      validFromDate = LocalDate.now(),
      validToDate = null,
      restrictionType = "UNSUPPORTED",
    )

    // When
    val responseSpec = callNotifyVSiPThatPersonRestrictionDeleted(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      notificationDto,
    )

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
    assertThat(testEventAuditRepository.getAuditCount(PRISONER_ALERTS_UPDATED_EVENT)).isEqualTo(0)
  }
}
