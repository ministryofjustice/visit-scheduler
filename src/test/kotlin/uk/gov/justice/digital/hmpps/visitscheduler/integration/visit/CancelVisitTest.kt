package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getCancelVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethodType.PHONE
import uk.gov.justice.digital.hmpps.visitscheduler.model.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@DisplayName("Put $VISIT_CANCEL")
@TestPropertySource(properties = ["visit.cancel.day-limit=7"])
class CancelVisitTest : IntegrationTestBase() {
  @Autowired
  protected lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  companion object {
    const val reservedByByUser = "reserved_by"
    const val cancelledByByUser = "canceled_by"
  }

  @Value("\${visit.cancel.day-limit:14}")
  var visitCancellationDayLimit: Long = 14

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @SpyBean
  private lateinit var visitNotificationEventRepository: VisitNotificationEventRepository

  @Test
  fun `cancel visit by reference -  with outcome and outcome text`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      PHONE,
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED, cancelVisitDto.actionedBy)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")

    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitCancelled.reference)

    Assertions.assertThat(eventAudit.type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    Assertions.assertThat(eventAudit.actionedBy).isEqualTo(cancelledByByUser)
    Assertions.assertThat(eventAudit.applicationMethodType).isEqualTo(PHONE)
    Assertions.assertThat(eventAudit.bookingReference).isEqualTo(visit.reference)
    Assertions.assertThat(eventAudit.sessionTemplateReference).isEqualTo(visit.sessionSlot.sessionTemplateReference)
    Assertions.assertThat(eventAudit.applicationReference).isEqualTo(visit.getLastApplication()?.reference)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel visit by reference -  with outcome and without outcome text`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        outcomeStatus = OutcomeStatus.VISITOR_CANCELLED,
      ),
      cancelledByByUser,
      NOT_KNOWN,
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.VISITOR_CANCELLED, cancelVisitDto.actionedBy, NOT_KNOWN)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(0)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel visit twice by reference - just send one event`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)
    val reference = visit.reference
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        outcomeStatus = OutcomeStatus.VISITOR_CANCELLED,
      ),
      cancelledByByUser,
      NOT_KNOWN,
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

    Assertions.assertThat(visit1.reference).isEqualTo(visit2.reference)
    Assertions.assertThat(visit1.applicationReference).isEqualTo(visit2.applicationReference)
    Assertions.assertThat(visit1.visitStatus).isEqualTo(visit2.visitStatus)

    // just one event thrown
    assertTelemetryClientEvents(visit1, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visit1)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel visit by reference - without outcome`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)
    val reference = visit.reference
    val eventsMap = mutableMapOf(
      "reference" to reference,
      "visitStatus" to visit.visitStatus.name,
    )
    // When
    val responseSpec = callCancelVisit(webTestClient, authHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference = reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent("prison-visit.cancelled-domain-event", eventsMap, null)
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel visit by reference - with outcome status of superseded`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.SUPERSEDED_CANCELLATION,
        "Prisoner has updated the existing booking",
      ),
      cancelledByByUser,
      NOT_KNOWN,
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.SUPERSEDED_CANCELLATION, cancelVisitDto.actionedBy, NOT_KNOWN)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner has updated the existing booking")
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel an updated visit by reference`() {
    // Given
    val bookedVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)
    val roles = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val sessionTemplate = sessionTemplateEntityHelper.create()

    // update the visit
    // first create a reserveVisitSlotDto with same details as the booked visit
    val createApplicationDto = CreateApplicationDto(
      prisonerId = bookedVisit.prisonerId,
      visitRestriction = bookedVisit.visitRestriction,
      sessionDate = bookedVisit.sessionSlot.slotDate,
      visitContact = ContactDto("John Smith", "011223344"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      actionedBy = reservedByByUser,
      sessionTemplateReference = sessionTemplate.reference,
    )

    // call visit change and then book the visit
    val responseSpecChange = callApplicationForVisitChange(webTestClient, roles, createApplicationDto, bookedVisit.reference)
    val responseSpecChangeResult = responseSpecChange
      .expectBody()
      .returnResult()
    val visit = objectMapper.readValue(responseSpecChangeResult.responseBody, VisitDto::class.java)
    callVisitBook(webTestClient, roles, visit.applicationReference)

    // finally cancel the updated visit
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )
    val reference = bookedVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED, cancelVisitDto.actionedBy, NOT_KNOWN)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)

    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel visit by reference - not found`() {
    // Given
    val reference = "12345"

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.ADMINISTRATIVE_CANCELLATION,
        "Visit does not exist",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    responseSpec.expectStatus().isNotFound

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  @Test
  fun `cancel visit by reference - access forbidden when no role`() {
    // Given
    val reference = "12345"

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.ESTABLISHMENT_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf()), reference, cancelVisitDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  @Test
  fun `cancel visit by reference - unauthorised when no token`() {
    // Given
    val reference = "12345"

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )

    // When
    val responseSpec = webTestClient.put().uri(getCancelVisitUrl(reference))
      .body(
        BodyInserters.fromValue(
          cancelVisitDto,
        ),
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `cancel expired visit returns bad request error`() {
    val yesterday = LocalDateTime.now().minusDays(visitCancellationDayLimit + 1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = yesterday.toLocalDate(), sessionTemplate = sessionTemplate)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )
    val reference = expiredVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - $reference is in the past, it cannot be cancelled")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
    verify(visitNotificationEventRepository, times(0)).deleteByBookingReference(eq(reference))
  }

  /**
   * the check for cancellations does not calculate time - only dates.
   */
  @Test
  fun `cancel expired visit on same day as allowed day does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )
    // Given
    val now = LocalDateTime.now().minusDays(visitCancellationDayLimit).truncatedTo(ChronoUnit.DAYS).withHour(1)
    val slotDate = now.toLocalDate()
    val visitStart = now.toLocalTime()

    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDate, visitStart = visitStart, sessionTemplate = sessionTemplate)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), expiredVisit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy, NOT_KNOWN)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visitCancelled.reference))
  }

  @Test
  fun `cancel expired visit on same day as today does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )
    // Given
    val oneAm = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withHour(1)
    val slotDate = oneAm.toLocalDate()
    val visitStart = oneAm.toLocalTime()
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDate, visitStart = visitStart, sessionTemplate = sessionTemplate)
    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy, NOT_KNOWN)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  @Test
  fun `cancel future visit does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      cancelledByByUser,
      applicationMethodType = NOT_KNOWN,
    )
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy, NOT_KNOWN)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))
  }

  fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(cancelledVisit.reference)

    verify(telemetryClient).trackEvent(
      eq(type.eventName),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(cancelledVisit.visitRoom)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus!!.name)
        Assertions.assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy)
        Assertions.assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
      },
      isNull(),
    )

    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitType" to cancelledVisit.visitType.name,
      "visitRoom" to cancelledVisit.visitRoom,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to cancelledVisit.visitStatus.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
      "actionedBy" to eventAudit.actionedBy,
      "applicationMethodType" to eventAudit.applicationMethodType.name,
    )
    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  @Test
  fun `when cancel visit by reference then any associated notifications are also deleted`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate)
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.NON_ASSOCIATION_EVENT)
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.PRISONER_RESTRICTION_CHANGE_EVENT)
    visitNotificationEventHelper.create(visit.reference, NotificationEventType.PRISONER_RELEASED_EVENT)

    var visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    Assertions.assertThat(visitNotifications.size).isEqualTo(3)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      PHONE,
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertHelper.assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED, cancelVisitDto.actionedBy)
    visitNotifications = visitNotificationEventHelper.getVisitNotifications(visit.reference)
    verify(visitNotificationEventRepository, times(1)).deleteByBookingReference(eq(visit.reference))

    Assertions.assertThat(visitNotifications.size).isEqualTo(0)
  }

  fun assertCancelledDomainEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }
}
