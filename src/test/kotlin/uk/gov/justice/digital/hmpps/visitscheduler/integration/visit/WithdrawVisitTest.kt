package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.WEBSITE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.REQUESTED_VISIT_WITHDRAWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason.VISIT_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.APPROVED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.REJECTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.REQUESTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus.WITHDRAWN
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.format.DateTimeFormatter

@DisplayName("Put $VISIT_CANCEL to withdraw a requested visit.")
class WithdrawVisitTest : IntegrationTestBase() {
  companion object {
    const val RESERVED_BY_USER = "reserved_by"
  }

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @Test
  fun `when a visit that was requested for the future is withdrawn the visit sub status is set to WITHDRAWN`() {
    // Given
    val requestedVisit = visitEntityHelper.create(
      visitStatus = BOOKED,
      visitSubStatus = REQUESTED,
      slotDate = startDate,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"),
    )

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        REQUESTED_VISIT_WITHDRAWN,
      ),
      RESERVED_BY_USER,
      PUBLIC,
      WEBSITE,
    )
    val reference = requestedVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertHelper.assertVisitCancellation(visitCancelled, REQUESTED_VISIT_WITHDRAWN, cancelVisitDto.actionedBy, applicationMethodType = WEBSITE, userType = PUBLIC, visitSubStatus = WITHDRAWN)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(0)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
    verify(visitNotificationEventServiceSpy, times(1)).deleteVisitAndPairedNotificationEvents(eq(requestedVisit.reference), eq(VISIT_CANCELLED), eq(null))
  }

  @Test
  fun `when withdraw a visit called twice for same visit reference reference - just send one event`() {
    // Given
    val requestedVisit = visitEntityHelper.create(
      visitStatus = BOOKED,
      visitSubStatus = REQUESTED,
      slotDate = startDate,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"),
    )

    val reference = requestedVisit.reference
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        outcomeStatus = REQUESTED_VISIT_WITHDRAWN,
      ),
      RESERVED_BY_USER,
      PUBLIC,
      WEBSITE,
    )

    // When
    val responseSpec1 = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)
    val responseSpec2 = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult1 = responseSpec1.expectStatus().isOk.expectBody().returnResult()
    val returnResult2 = responseSpec2.expectStatus().isOk.expectBody().returnResult()

    // And
    val visit1 = objectMapper.readValue(returnResult1.responseBody, VisitDto::class.java)
    val visit2 = objectMapper.readValue(returnResult2.responseBody, VisitDto::class.java)

    assertThat(visit1.reference).isEqualTo(visit2.reference)
    assertThat(visit1.applicationReference).isEqualTo(visit2.applicationReference)
    assertThat(visit1.visitStatus).isEqualTo(visit2.visitStatus)

    // just one event thrown
    assertTelemetryClientEvents(visit1, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visit1)
    verify(visitNotificationEventServiceSpy, times(1)).deleteVisitAndPairedNotificationEvents(eq(requestedVisit.reference), eq(VISIT_CANCELLED), eq(null))
  }

  @Test
  fun `when a visit that was requested in the past is withdrawn the visit sub status is set to WITHDRAWN`() {
    // Given
    val requestedVisit = visitEntityHelper.create(
      visitStatus = BOOKED,
      visitSubStatus = REQUESTED,
      // visit was requested in the past
      slotDate = startDate.minusWeeks(4),
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"),
    )

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        REQUESTED_VISIT_WITHDRAWN,
      ),
      RESERVED_BY_USER,
      PUBLIC,
      WEBSITE,
    )
    val reference = requestedVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertHelper.assertVisitCancellation(visitCancelled, REQUESTED_VISIT_WITHDRAWN, cancelVisitDto.actionedBy, applicationMethodType = WEBSITE, userType = PUBLIC, visitSubStatus = WITHDRAWN)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(0)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
    verify(visitNotificationEventServiceSpy, times(1)).deleteVisitAndPairedNotificationEvents(eq(requestedVisit.reference), eq(VISIT_CANCELLED), eq(null))
  }

  @Test
  fun `when an approved visit is withdrawn the visit sub status is not set to WITHDRAWN`() {
    // Given
    val requestedVisit = visitEntityHelper.create(
      visitStatus = BOOKED,
      visitSubStatus = APPROVED,
      slotDate = startDate,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"),
    )

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        REQUESTED_VISIT_WITHDRAWN,
      ),
      RESERVED_BY_USER,
      PUBLIC,
      WEBSITE,
    )
    val reference = requestedVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertHelper.assertVisitCancellation(visitCancelled, REQUESTED_VISIT_WITHDRAWN, cancelVisitDto.actionedBy, applicationMethodType = WEBSITE, userType = PUBLIC, visitSubStatus = VisitSubStatus.CANCELLED)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(0)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
    verify(visitNotificationEventServiceSpy, times(1)).deleteVisitAndPairedNotificationEvents(eq(requestedVisit.reference), eq(VISIT_CANCELLED), eq(null))
  }

  @Test
  fun `when an already rejected visit is withdrawn the visit sub status is not set to WITHDRAWN`() {
    // Given
    val requestedVisit = visitEntityHelper.create(
      visitStatus = VisitStatus.CANCELLED,
      visitSubStatus = REJECTED,
      slotDate = startDate,
      sessionTemplate = sessionTemplateDefault,
      visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"),
    )

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        REQUESTED_VISIT_WITHDRAWN,
      ),
      RESERVED_BY_USER,
      PUBLIC,
      WEBSITE,
    )
    val reference = requestedVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(visitCancelled.reference).isEqualTo(reference)
    verify(telemetryClient, times(0)).trackEvent(any(), any(), any())
    verify(visitNotificationEventServiceSpy, times(0)).deleteVisitAndPairedNotificationEvents(eq(requestedVisit.reference), eq(VISIT_CANCELLED), eq(null))
  }

  fun assertCancelledDomainEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }

  fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        assertThat(it["visitSubStatus"]).isEqualTo(cancelledVisit.visitSubStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(cancelledVisit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo(((cancelledVisit.visitContact.telephone != null).toString()))
        assertThat(it["hasEmail"]).isEqualTo(((cancelledVisit.visitContact.email != null).toString()))
        assertThat(it["totalVisitors"]).isEqualTo(cancelledVisit.visitors.size.toString())
        val commaDelimitedVisitorIds = cancelledVisit.visitors.map { it.nomisPersonId }.joinToString(",")
        assertThat(it["visitors"]).isEqualTo(commaDelimitedVisitorIds)
        eventAudit.actionedBy.userName?.let { value ->
          assertThat(it["actionedBy"]).isEqualTo(value)
        }
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
        assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus?.name)
      },
      isNull(),
    )

    val actionedBy = if (eventAudit.actionedBy.userType == STAFF) eventAudit.actionedBy.userName else eventAudit.actionedBy.bookerReference
    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitStatus" to cancelledVisit.visitStatus.name,
      "visitSubStatus" to cancelledVisit.visitSubStatus.name,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitEnd" to cancelledVisit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitType" to cancelledVisit.visitType.name,
      "visitRoom" to cancelledVisit.visitRoom,
      "hasPhoneNumber" to ((cancelledVisit.visitContact.telephone != null).toString()),
      "hasEmail" to ((cancelledVisit.visitContact.email != null).toString()),
      "totalVisitors" to (cancelledVisit.visitors.size.toString()),
      "visitors" to (cancelledVisit.visitors.map { it.nomisPersonId }.joinToString(",")),
      "actionedBy" to actionedBy,
      "source" to eventAudit.actionedBy.userType.name,
      "applicationMethodType" to eventAudit.applicationMethodType.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
    )

    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }
}
