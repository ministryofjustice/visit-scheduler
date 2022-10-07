package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
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

@DisplayName("Put $VISIT_CANCEL")
class CancelVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = visitEntityHelper.deleteAll()

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
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.CANCELLED.name)
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.PRISONER_CANCELLED.name)
      .jsonPath("$.visitNotes.length()").isEqualTo(1)
      .jsonPath("$.visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("Prisoner got covid")
      .returnResult()

    // And
    val visitUpdated = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visitUpdated.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(visitUpdated.outcomeStatus!!.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-cancelled"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
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
      .jsonPath("$.visitNotes").isEmpty
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.VISITOR_CANCELLED.name)
      .returnResult()

    // And
    val visitUpdated = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visitUpdated.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(visitUpdated.outcomeStatus!!.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-cancelled"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }

  @Test
  fun `cancel visit by reference - without outcome`() {

    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)
    val reference = visit.reference

    // When
    val responseSpec = callCancelVisit(webTestClient, authHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference = reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
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
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.CANCELLED.name)
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.SUPERSEDED_CANCELLATION.name)
      .jsonPath("$.visitNotes.length()").isEqualTo(1)
      .jsonPath("$.visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("Prisoner has updated the existing booking")
      .returnResult()

    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(VisitStatus.CANCELLED.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(OutcomeStatus.SUPERSEDED_CANCELLATION.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-cancelled"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
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
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = LocalDateTime.now().minusDays(2))

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
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - $reference is in the past, it cannot be cancelled")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
  }
}
