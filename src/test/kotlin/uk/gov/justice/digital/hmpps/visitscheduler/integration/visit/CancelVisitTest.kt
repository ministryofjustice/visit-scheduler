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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getCancelVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
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

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertTelemetryClientEvents(visitCancelled, telemetryClient)
  }

  @Test
  fun `cancel visit by reference -  with outcome and without outcome text`() {

    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)

    val outcomeDto = OutcomeDto(
      outcomeStatus = OutcomeStatus.VISITOR_CANCELLED
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.VISITOR_CANCELLED)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(0)
    assertTelemetryClientEvents(visitCancelled, telemetryClient)
  }

  @Test
  fun `cancel visit twice by reference - just send one event`() {

    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)
    val reference = visit.reference
    val outcomeDto = OutcomeDto(
      outcomeStatus = OutcomeStatus.VISITOR_CANCELLED
    )

    // When
    val responseSpec1 = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)
    val responseSpec2 = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    val returnResult1 = responseSpec1.expectStatus().isOk.expectBody().returnResult()
    val returnResult2 = responseSpec2.expectStatus().isOk.expectBody().returnResult()

    // And
    val visitCancelled1 = objectMapper.readValue(returnResult1.responseBody, VisitDto::class.java)
    val visitCancelled2 = objectMapper.readValue(returnResult2.responseBody, VisitDto::class.java)

    Assertions.assertThat(visitCancelled1).isEqualTo(visitCancelled2)

    // just one event thrown
    assertTelemetryClientEvents(visitCancelled1, telemetryClient)
  }

  @Test
  fun `cancel visit by reference - without outcome`() {

    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)
    val reference = visit.reference
    val eventsMap = mutableMapOf(
      "reference" to reference,
      "visitStatus" to visit.visitStatus.name
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

    val outcomeDto = OutcomeDto(
      OutcomeStatus.SUPERSEDED_CANCELLATION,
      "Prisoner has updated the existing booking"
    )
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.SUPERSEDED_CANCELLATION)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner has updated the existing booking")
  }

  @Test
  fun `cancel visit by reference - not found`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ADMINISTRATIVE_CANCELLATION,
      "Visit does not exist"
    )

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    responseSpec.expectStatus().isNotFound

    // And
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
  }

  @Test
  fun `cancel visit by reference - access forbidden when no role`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ESTABLISHMENT_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf()), reference, outcomeDto)

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

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = webTestClient.put().uri(getCancelVisitUrl(reference))
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `cancel expired visit returns bad request error`() {
    val visitStart = LocalDateTime.now().minusDays(visitCancellationDayLimit + 1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )
    val reference = expiredVisit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

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
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().minusDays(visitCancellationDayLimit).truncatedTo(ChronoUnit.DAYS).withHour(1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), expiredVisit.reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  @Test
  fun `cancel expired visit on same day as today does not return error`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withHour(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  @Test
  fun `cancel future visit does not return error`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().plusDays(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = callCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  companion object {

    fun assertVisitCancellation(
      cancelledVisit: VisitDto,
      expectedOutcomeStatus: OutcomeStatus
    ) {
      Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
      Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
    }

    fun assertTelemetryClientEvents(
      cancelledVisit: VisitDto,
      telemetryClient: TelemetryClient
    ) {
      verify(telemetryClient).trackEvent(
        eq("visit-cancelled"),
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
        },
        isNull()
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
        "outcomeStatus" to cancelledVisit.outcomeStatus!!.name
      )
      verify(telemetryClient, times(1)).trackEvent("visit-cancelled", eventsMap, null)

      verify(telemetryClient).trackEvent(
        eq("prison-visit.cancelled-domain-event"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
    }
  }
}
