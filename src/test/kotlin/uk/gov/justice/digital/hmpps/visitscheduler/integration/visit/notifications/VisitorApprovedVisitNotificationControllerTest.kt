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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_VISITOR_APPROVED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.VisitorApprovedUnapprovedNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatVisitorApproved
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_VISITOR_APPROVED_PATH")
class VisitorApprovedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "AA11BCC"
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
  fun `when visitor is re-approved then then flagged visits are un-flagged`() {
    // Given
    val notificationDto = VisitorApprovedUnapprovedNotificationDto(visitorId = visitorId, prisonerNumber = prisonerId)

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
    val visit = visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visit.reference, NotificationEventType.VISITOR_UNAPPROVED_EVENT))

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, prisonCode)
    whenever(prisonerService.getPrisonerPrisonCodeFromPrisonId(prisonerId)).thenReturn(prisonCode)

    // When
    val responseSpec = callNotifyVSiPThatVisitorApproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.VISITOR_UNAPPROVED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.VISITOR_APPROVED.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  @Test
  fun `when visitor is from an unsupported prison and is re-approved then processing is skipped`() {
    // Given
    val notificationDto = VisitorApprovedUnapprovedNotificationDto(visitorId = visitorId, prisonerNumber = prisonerId)

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
    val visit = visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)

    testVisitNotificationEventRepository.saveAndFlush(VisitNotificationEvent(visit.reference, NotificationEventType.VISITOR_UNAPPROVED_EVENT))

    prisonOffenderSearchMockServer.stubGetPrisonerByString(prisonerId, "XYZ")
    whenever(prisonerService.getPrisonerPrisonCodeFromPrisonId(prisonerId)).thenReturn(null)

    // When
    val responseSpec = callNotifyVSiPThatVisitorApproved(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk

    verify(visitNotificationEventRepository, times(0)).deleteAll()
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
