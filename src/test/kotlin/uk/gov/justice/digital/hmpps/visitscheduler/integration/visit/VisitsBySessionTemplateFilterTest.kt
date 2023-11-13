package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISITS_BY_SESSION_TEMPLATE_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import java.time.LocalDateTime

@DisplayName("GET /visits/{sessionTemplateReference}")
class VisitsBySessionTemplateFilterTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2023, 11, 1, 11, 30, 0)

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun createVisits() {
    // visit 1 booked for session template reference - session-1
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000AA", sessionTemplateReference = "session-1", reference = "visit-booked-1", visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.OPEN)
    // visit 2 reserved for session template reference - session-1
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime.plusMinutes(1), prisonerId = "FF0000AA", sessionTemplateReference = "session-1", reference = "visit-reserved-1", visitStatus = VisitStatus.RESERVED)
    // visit 3 booked for session template reference - session-2
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000BB", sessionTemplateReference = "session-2", reference = "visit-booked-2", visitStatus = VisitStatus.BOOKED)
    // visit 4 booked for session template reference - session-1 but on next day
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime.plusDays(1), prisonerId = "FF0000BB", sessionTemplateReference = "session-1", reference = "visit-booked-3", visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.CLOSED)
  }

  @Test
  fun `get booked visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = "session-1"
    val sessionDate = visitTime.toLocalDate()

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-1")
  }

  @Test
  fun `get booked and reserved visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = "session-1"
    val sessionDate = visitTime.toLocalDate()

    // When
    val responseSpec = callVisitsBySessionEndPoint(
      sessionTemplateReference,
      "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED,RESERVED",
    )

    // Then
    responseSpec.expectStatus().isOk

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].reference").isEqualTo("visit-reserved-1")
      .jsonPath("$.content[1].reference").isEqualTo("visit-booked-1")
  }

  @Test
  fun `get booked visits by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = "session-1"
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-3")
      .jsonPath("$.content[1].reference").isEqualTo("visit-booked-1")
  }

  @Test
  fun `get booked visits with restriction type OPEN by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = "session-1"
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestriction=OPEN")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-1")
      .jsonPath("$.content[0].visitRestriction").isEqualTo("OPEN")
  }


  @Test
  fun `get booked visits with restriction type OPEN or CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = "session-1"
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestriction=OPEN,CLOSED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-3")
      .jsonPath("$.content[0].visitRestriction").isEqualTo("CLOSED")
      .jsonPath("$.content[1].reference").isEqualTo("visit-booked-1")
      .jsonPath("$.content[1].visitRestriction").isEqualTo("OPEN")
  }

  @Test
  fun `get booked visits with restriction type CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = "session-1"
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestriction=CLOSED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-3")
      .jsonPath("$.content[0].visitRestriction").isEqualTo("CLOSED")
  }

  @Test
  fun `get booked and reserved visits by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = "session-1"
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-3")
      .jsonPath("$.content[1].reference").isEqualTo("visit-reserved-1")
      .jsonPath("$.content[2].reference").isEqualTo("visit-booked-1")
  }

  @Test
  fun `when session has no visits for a date no records are returned`() {
    // Given
    val sessionTemplateReference = "session-2"
    val sessionDate = visitTime.toLocalDate().plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(0)
  }

  @Test
  fun `access forbidden when no role`() {
    val sessionTemplateReference = "session-1"
    val sessionDate = visitTime.toLocalDate()

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED", roles = listOf())

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val prisonerId = "FF0000AA"
    val prisonId = "MDI"

    // When
    val responseSpec = webTestClient.get().uri("prisonId=$prisonId&prisonerId=$prisonerId").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitsBySessionEndPoint(
    sessionTemplateReference: String,
    params: String,
    page: Int = 0,
    pageSize: Int = 100,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    var uri = GET_VISITS_BY_SESSION_TEMPLATE_REFERENCE.replace("{sessionTemplateReference}", sessionTemplateReference)
    uri += "$params&page=$page&size=$pageSize"
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
