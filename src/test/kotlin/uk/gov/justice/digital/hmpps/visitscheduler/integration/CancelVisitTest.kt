package uk.gov.justice.digital.hmpps.visitscheduler.integration

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
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CANCEL
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@DisplayName("Put $VISIT_CANCEL")
class CancelVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `cancel visit by reference with outcome and outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = callCancelVisit(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

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
      eq("visit-scheduler-prison-visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visitUpdated.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(visitUpdated.outcomeStatus!!.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-cancelled"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit.cancelled-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.cancelled-event"), any(), isNull())
  }

  @Test
  fun `cancel visit by reference with outcome and without outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      outcomeStatus = OutcomeStatus.VISITOR_CANCELLED
    )

    // When

    val responseSpec = callCancelVisit(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitNotes").isEmpty
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.VISITOR_CANCELLED.name)
      .returnResult()

    // And
    val visitUpdated = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visitUpdated.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(visitUpdated.outcomeStatus!!.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-cancelled"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit.cancelled-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitUpdated.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.cancelled-event"), any(), isNull())
  }

  @Test
  fun `cancel visit by reference without outcome`() {

    // Given
    val visit = createVisitAndSave()

    // When
    val responseSpec = callCancelVisit(authHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference = visit.reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit.cancelled-event"), any(), isNull())
  }

  @Test
  fun `cancel visit by reference with outcome status of superseded`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      OutcomeStatus.SUPERSEDED_CANCELLATION,
      "Prisoner has updated the existing booking"
    )

    // When
    val responseSpec = callCancelVisit(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), visit.reference, outcomeDto)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.CANCELLED.name)
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.SUPERSEDED_CANCELLATION.name)
      .jsonPath("$.visitNotes.length()").isEqualTo(1)
      .jsonPath("$.visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("Prisoner has updated the existing booking")
      .returnResult()
  }

  @Test
  fun `put visit by reference - not found`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ADMINISTRATIVE_CANCELLATION,
      "Visit does not exist"
    )

    // When
    val responseSpec = callCancelVisit(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")), reference, outcomeDto)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ESTABLISHMENT_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = callCancelVisit(setAuthorisation(roles = listOf()), reference, outcomeDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
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

  private fun callCancelVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    reference: String,
    outcome: OutcomeDto? = null
  ): ResponseSpec {

    if (outcome == null) {
      return webTestClient.patch().uri(getCancelVisitUrl(reference))
        .headers(authHttpHeaders)
        .exchange()
    }

    return webTestClient.patch().uri(getCancelVisitUrl(reference))
      .headers(authHttpHeaders)
      .body(BodyInserters.fromValue(outcome))
      .exchange()
  }

  private fun getCancelVisitUrl(reference: String): String {
    return VISIT_CANCEL.replace("{reference}", reference)
  }

  private fun createVisitAndSave(): Visit {
    val visit = visitCreator(visitRepository)
      .withVisitStatus(BOOKED)
      .save()

    visitRepository.saveAndFlush(visit)
    return visit
  }
}
