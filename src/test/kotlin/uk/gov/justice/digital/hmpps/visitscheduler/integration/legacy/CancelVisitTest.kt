package uk.gov.justice.digital.hmpps.visitscheduler.integration.legacy

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@DisplayName("Put /visits/{reference}/cancel")
@TestPropertySource(properties = ["visit.cancel.day-limit=7"])
class CancelVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Value("\${visit.cancel.day-limit:14}")
  var visitCancellationDayLimit: Long = 14

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = visitEntityHelper.deleteAll()

  @Test
  fun `cancel visit by reference with outcome and outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.PRISONER_CANCELLED)
    assertTelemetryClientEvents(visitCancelled)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
  }

  @Test
  fun `cancel visit by reference with outcome and without outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      outcomeStatus = OutcomeStatus.VISITOR_CANCELLED
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.VISITOR_CANCELLED)
    assertTelemetryClientEvents(visitCancelled)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(0)
  }

  @Test
  fun `cancel visit by reference without outcome`() {

    // Given
    val visit = createVisitAndSave()

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit.cancelled-domain-event"), any(), isNull())
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
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.SUPERSEDED_CANCELLATION)
    Assertions.assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    Assertions.assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner has updated the existing booking")
    assertTelemetryClientEvents(visitCancelled)
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
    val responseSpec = webTestClient.patch().uri("/visits/$reference/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

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
    val responseSpec = webTestClient.patch().uri("/visits/$reference/cancel")
      .headers(setAuthorisation(roles = listOf()))
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
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
    val responseSpec = webTestClient.put().uri("/visits/$reference/cancel")
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
  fun `cancel expired visit before allowed days returns error`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().minusDays(visitCancellationDayLimit + 1)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${expiredVisit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - ${expiredVisit.reference} is in the past, it cannot be cancelled")

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
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  @Test
  fun `cancel expired visit today does not return error`() {
    val outcomeDto = OutcomeDto(
      OutcomeStatus.CANCELLATION,
      "No longer joining."
    )
    // Given
    val visitStart = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS).withHour(1)
    val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

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
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
  }

  private fun createVisitAndSave(): Visit {
    return visitEntityHelper.create(visitStatus = BOOKED)
  }

  private fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus
  ) {
    Assertions.assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    Assertions.assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
  }

  private fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto
  ) {
    verify(telemetryClient).trackEvent(
      eq("visit-cancelled"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        Assertions.assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus!!.name)
      },
      isNull()
    )

    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
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

  @Nested
  @DisplayName("Cancellation days have been set as zero")
  @TestPropertySource(properties = ["visit.cancel.day-limit=0"])
  inner class ZeroCancellationDays {
    @Value("\${visit.cancel.day-limit}")
    var visitCancellationDayLimit: Long = -7

    @AfterEach
    internal fun deleteAllVisits() = visitEntityHelper.deleteAll()

    @Test
    fun `when cancel day limit configured as zero cancel future visit does not return error`() {
      val outcomeDto = OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining."
      )
      // Given
      val visitStart = LocalDateTime.now().plusDays(1)
      val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

      // When
      val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(outcomeDto)
        )
        .exchange()

      // Then
      Assertions.assertThat(visitCancellationDayLimit).isEqualTo(0)

      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      // And
      val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
      assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
    }
  }

  @Nested
  @DisplayName("Cancellation days have been set as a negative value")
  @TestPropertySource(properties = ["visit.cancel.day-limit=-2"])
  inner class NegativeCancellationDays {
    @Value("\${visit.cancel.day-limit}")
    var visitCancellationDayLimit: Long = -7

    @AfterEach
    internal fun deleteAllVisits() = visitEntityHelper.deleteAll()

    @Test
    fun `when cancel day limit configured as a negative value cancel future visit does not return error`() {
      val outcomeDto = OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining."
      )
      // Given
      val visitStart = LocalDateTime.now().plusDays(1)
      val visit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

      // When
      val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(outcomeDto)
        )
        .exchange()

      // Then
      Assertions.assertThat(visitCancellationDayLimit).isEqualTo(-2)

      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      // And
      val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
      assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
    }

    @Test
    fun `cancel expired visit before current time returns error when cancel day limit configured as a negative value`() {
      val outcomeDto = OutcomeDto(
        OutcomeStatus.CANCELLATION,
        "No longer joining."
      )
      // Given
      // visit has expired based on current date
      // as the configured limit is -2 which is incorrect - any cancellations before current time sohuld be allowed
      val visitStart = LocalDateTime.now().minusMinutes(10)
      val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart)

      // When
      val responseSpec = webTestClient.patch().uri("/visits/${expiredVisit.reference}/cancel")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(outcomeDto)
        )
        .exchange()

      // Then
      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      // And
      val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
      assertVisitCancellation(visitCancelled, OutcomeStatus.CANCELLATION)
    }
  }
}
