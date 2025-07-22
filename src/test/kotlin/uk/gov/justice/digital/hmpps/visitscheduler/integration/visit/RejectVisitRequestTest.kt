package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REQUESTS_REJECT_VISIT_BY_REFERENCE_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApproveRejectionVisitRequestBodyDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRejectVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository

@Transactional(propagation = SUPPORTS)
@DisplayName("Get $VISIT_REQUESTS_REJECT_VISIT_BY_REFERENCE_PATH")
class RejectVisitRequestTest : IntegrationTestBase() {

  @Autowired
  private lateinit var testEventAuditRepository: TestEventAuditRepository

  @Autowired
  private lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @Autowired
  private lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  @MockitoSpyBean
  lateinit var telemetryClient: TelemetryClient

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when reject visit requests endpoint is called, then visit is successfully rejected and domain event is raised`() {
    // Given
    val visitPrimary = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = VisitRestriction.OPEN, visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT)

    val rejectVisitRequestBodyDto = ApproveRejectionVisitRequestBodyDto(visitReference = visitPrimary.reference, actionedBy = "user1")

    // When
    val responseSpec = callRejectVisitRequest(webTestClient, visitPrimary.reference, rejectVisitRequestBodyDto, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val rejectedVisit = getRejectVisitRequestResponse(responseSpec)
    assertThat(rejectedVisit.reference).isEqualTo(visitPrimary.reference)
    assertThat(rejectedVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(rejectedVisit.visitSubStatus).isEqualTo(VisitSubStatus.REJECTED)

    testEventAuditRepository.findAllByBookingReference(visitPrimary.reference).let {
      val types = it.map { event -> event.type }
      assertThat(types).containsExactlyInAnyOrder(
        EventAuditType.REQUESTED_VISIT,
        EventAuditType.REQUESTED_VISIT_REJECTED,
      )
    }

    verify(telemetryClient).trackEvent(
      eq("visit-request-rejected"),
      argThat { map ->
        assertThat(map["reference"]).isEqualTo(visitPrimary.reference)
        assertThat(map["visitStatus"]).isEqualTo(VisitStatus.CANCELLED.name)
        assertThat(map["visitSubStatus"]).isEqualTo(VisitSubStatus.REJECTED.name)
        true
      },
      isNull(),
    )

    assertVisitRequestActionedDomainEvent(visitPrimary.reference)
  }

  @Test
  fun `when reject visit requests endpoint is called and visit has flags, then visit is successfully rejected and is un-flagged`() {
    // Given
    val visitPrimary = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = VisitRestriction.OPEN, visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT)

    visitNotificationEventHelper.create(visit = visitPrimary, notificationEventType = NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT)

    val rejectVisitRequestBodyDto = ApproveRejectionVisitRequestBodyDto(visitReference = visitPrimary.reference, actionedBy = "user1")

    // When
    val responseSpec = callRejectVisitRequest(webTestClient, visitPrimary.reference, rejectVisitRequestBodyDto, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val rejectedVisit = getRejectVisitRequestResponse(responseSpec)
    assertThat(rejectedVisit.reference).isEqualTo(visitPrimary.reference)
    assertThat(rejectedVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(rejectedVisit.visitSubStatus).isEqualTo(VisitSubStatus.REJECTED)

    testEventAuditRepository.findAllByBookingReference(visitPrimary.reference).let {
      val types = it.map { event -> event.type }
      assertThat(types).containsExactlyInAnyOrder(
        EventAuditType.REQUESTED_VISIT,
        EventAuditType.REQUESTED_VISIT_REJECTED,
      )
    }

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      check {
        assertThat(it["reference"]).isEqualTo(visitPrimary.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.PRISONER_ALERTS_UPDATED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.VISIT_REQUEST_REJECTED.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())

    assertVisitRequestActionedDomainEvent(visitPrimary.reference)
  }

  @Test
  fun `when reject visit requests endpoint is called with bad request body, then bad request is returned`() {
    // Given
    val visitPrimary = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = VisitRestriction.OPEN, visitStatus = BOOKED, visitSubStatus = VisitSubStatus.REQUESTED)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT)

    // When
    val responseSpec = callRejectVisitRequest(webTestClient, visitPrimary.reference, null, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when reject visit requests endpoint is called, but requested visit is not in correct sub status, then bad request is returned`() {
    // Given
    val visitPrimary = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = VisitRestriction.OPEN, visitStatus = VisitStatus.CANCELLED, visitSubStatus = VisitSubStatus.REJECTED)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visitPrimary, type = EventAuditType.REQUESTED_VISIT_REJECTED)

    val rejectVisitRequestBodyDto = ApproveRejectionVisitRequestBodyDto(visitReference = visitPrimary.reference, actionedBy = "user1")

    // When
    val responseSpec = callRejectVisitRequest(webTestClient, visitPrimary.reference, rejectVisitRequestBodyDto, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  private fun getRejectVisitRequestResponse(responseSpec: WebTestClient.ResponseSpec): VisitDto = objectMapper.readValue(responseSpec.expectBody().returnResult().responseBody, VisitDto::class.java)

  private fun assertVisitRequestActionedDomainEvent(visitReference: String) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit-request.actioned-domain-event"),
      check {
        assertThat(it["reference"]).isEqualTo(visitReference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit-request.actioned-domain-event"), any(), isNull())
  }
}
