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
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getCancelVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethod.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethod.PHONE
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents.VISIT_CANCELLED_EVENT
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@DisplayName("Put $VISIT_CANCEL")
@TestPropertySource(properties = ["visit.cancel.day-limit=7"])
class CancelVisitTest : IntegrationTestBase() {
  @Value("\${visit.cancel.day-limit:14}")
  var visitCancellationDayLimit: Long = 14

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun `cancel visit by reference -  with outcome and outcome text`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)

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
    assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED, cancelVisitDto.actionedBy)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    Assertions.assertThat(visitCancelled.createdBy).isEqualTo(visit.createdBy)
    Assertions.assertThat(visitCancelled.updatedBy).isEqualTo(visit.updatedBy)
    Assertions.assertThat(visitCancelled.lastApplicationMethod).isEqualTo(PHONE)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
  }

  @Test
  fun `cancel visit by reference -  with outcome and without outcome text`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)

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
    assertVisitCancellation(visitCancelled, OutcomeStatus.VISITOR_CANCELLED, cancelVisitDto.actionedBy)
    Assertions.assertThat(visitCancelled.createdBy).isEqualTo(visit.createdBy)
    Assertions.assertThat(visitCancelled.updatedBy).isEqualTo(visit.updatedBy)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(0)

    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
  }

  @Test
  fun `cancel visit twice by reference - just send one event`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)
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
  }

  @Test
  fun `cancel visit by reference - without outcome`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)
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
  }

  @Test
  fun `cancel visit by reference - with outcome status of superseded`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)

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
    assertVisitCancellation(visitCancelled, OutcomeStatus.SUPERSEDED_CANCELLATION, cancelVisitDto.actionedBy)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner has updated the existing booking")
    Assertions.assertThat(visitCancelled.createdBy).isEqualTo(visit.createdBy)
    Assertions.assertThat(visitCancelled.updatedBy).isEqualTo(visit.updatedBy)
  }

  @Test
  fun `cancel an updated visit by reference`() {
    // Given
    val bookedVisit = visitEntityHelper.create(visitStatus = BOOKED)
    val roles = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val sessionTemplate = sessionTemplateEntityHelper.create()

    // update the visit
    // first create a reserveVisitSlotDto with same details as the booked visit
    val reserveVisitSlotDto = ReserveVisitSlotDto(
      prisonerId = bookedVisit.prisonerId,
      visitRestriction = bookedVisit.visitRestriction,
      startTimestamp = bookedVisit.visitStart,
      endTimestamp = bookedVisit.visitEnd,
      visitContact = ContactDto("John Smith", "011223344"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      actionedBy = reservedByByUser,
      sessionTemplateReference = sessionTemplate.reference,
      applicationMethod = NOT_KNOWN,
    )

    // call visit change and then book the visit
    val responseSpecChange = callVisitChange(webTestClient, roles, reserveVisitSlotDto, bookedVisit.reference)
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
      applicationMethod = NOT_KNOWN,
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
    assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED, cancelVisitDto.actionedBy)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    Assertions.assertThat(visitCancelled.createdBy).isEqualTo(bookedVisit.createdBy)
    Assertions.assertThat(visitCancelled.updatedBy).isEqualTo(reservedByByUser)
    assertTelemetryClientEvents(visitCancelled, VISIT_CANCELLED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
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
      applicationMethod = NOT_KNOWN,
    )

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, cancelVisitDto)

    // Then
    responseSpec.expectStatus().isNotFound

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
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
      applicationMethod = NOT_KNOWN,
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
      applicationMethod = NOT_KNOWN,
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
    val visitStart = LocalDateTime.now().minusDays(visitCancellationDayLimit + 1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
      applicationMethod = NOT_KNOWN,
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
      applicationMethod = NOT_KNOWN,
    )
    // Given
    val visitStart = LocalDateTime.now().minusDays(visitCancellationDayLimit).truncatedTo(ChronoUnit.DAYS).withHour(1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), expiredVisit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy)
  }

  @Test
  fun `cancel expired visit on same day as today does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      cancelledByByUser,
      applicationMethod = NOT_KNOWN,
    )
    // Given
    val visitStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withHour(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy)
  }

  @Test
  fun `cancel future visit does not return error`() {
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining.",
      ),
      cancelledByByUser,
      applicationMethod = NOT_KNOWN,
    )
    // Given
    val visitStart = LocalDateTime.now().plusDays(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION, cancelVisitDto.actionedBy)
  }

  fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
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
        Assertions.assertThat(it["actionedBy"]).isEqualTo(cancelledVisit.cancelledBy)
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
      "actionedBy" to cancelledVisit.cancelledBy,
    )
    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
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

  companion object {
    const val reservedByByUser = "user-1"
    const val cancelledByByUser = "user-2"

    fun assertVisitCancellation(
      cancelledVisit: VisitDto,
      expectedOutcomeStatus: OutcomeStatus,
      cancelledBy: String,
    ) {
      Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
      Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
      Assertions.assertThat(cancelledVisit.cancelledBy).isEqualTo(cancelledBy)
    }
  }
}
