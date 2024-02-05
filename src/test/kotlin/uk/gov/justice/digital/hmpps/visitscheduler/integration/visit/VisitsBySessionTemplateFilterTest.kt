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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISITS_BY
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.ArrayList

@DisplayName("GET /visits/session-template")
class VisitsBySessionTemplateFilterTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2023, 11, 1, 11, 30, 0)

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun createVisits() {
    // visit 1 booked for session template reference - session-1
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000AA", sessionTemplateReference = "session-1", reference = "visit-booked-1", visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.OPEN)
    // visit 2 reserved for session template reference - session-1
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000AA", sessionTemplateReference = "session-1", reference = "visit-reserved-1", visitStatus = VisitStatus.CANCELLED)
    // visit 3 booked for session template reference - session-2
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000BB", sessionTemplateReference = "session-2", reference = "visit-booked-2", visitStatus = VisitStatus.BOOKED)
    // visit 4 booked for session template reference - session-1 but on next day
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime.plusDays(1), prisonerId = "FF0000BB", sessionTemplateReference = "session-1", reference = "visit-booked-3", visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.CLOSED)
    // session template reference is null and status is BOOKED
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime.plusDays(1), prisonerId = "FF0000BB", sessionTemplateReference = null, reference = "visit-booked-sess-null", visitStatus = VisitStatus.BOOKED, visitRestriction = VisitRestriction.UNKNOWN)
    // session template reference is null and status is CANCELLED
    visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime.plusDays(2), prisonerId = "FF0000BB", sessionTemplateReference = null, reference = "visit-booked-sess-null2", visitStatus = VisitStatus.CANCELLED, visitRestriction = VisitRestriction.UNKNOWN)
  }

  @Test
  fun `get booked visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = "session-1"
    val sessionDate = visitTime.toLocalDate()

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(VisitStatus.BOOKED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(VisitStatus.BOOKED, VisitStatus.CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED), visitRestrictions = listOf(VisitRestriction.OPEN)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED), visitRestrictions = listOf(VisitRestriction.OPEN, VisitRestriction.CLOSED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED), visitRestrictions = listOf(VisitRestriction.CLOSED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED, VisitStatus.CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-3")
      .jsonPath("$.content[1].reference").isEqualTo("visit-reserved-1")
      .jsonPath("$.content[2].reference").isEqualTo("visit-booked-1")
  }

  @Test
  fun `when session template reference passed is null only session template reference null records are returned`() {
    // Given
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(2)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference = null, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.BOOKED, VisitStatus.CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-sess-null2")
      .jsonPath("$.content[1].reference").isEqualTo("visit-booked-sess-null")
  }

  @Test
  fun `when session template reference passed is null only session template reference null records matching status are returned`() {
    // Given
    val fromDate = visitTime.toLocalDate()
    val toDate = visitTime.toLocalDate().plusDays(2)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference = null, fromDate = fromDate, toDate = toDate, visitStatus = listOf(VisitStatus.CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].reference").isEqualTo("visit-booked-sess-null2")
  }

  @Test
  fun `when session has no visits for a date no records are returned`() {
    // Given
    val sessionTemplateReference = "session-2"
    val sessionDate = visitTime.toLocalDate().plusDays(1)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(VisitStatus.BOOKED, VisitStatus.CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(VisitStatus.BOOKED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params, roles = listOf())

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val sessionTemplateReference = "FF0000AA"
    val sessionDate = LocalDate.now()

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(VisitStatus.BOOKED)).joinToString("&")
    val responseSpec = webTestClient.get().uri("/visits/session-template?$params").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitsBySessionEndPoint(
    params: String,
    page: Int = 0,
    pageSize: Int = 100,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = "$GET_VISITS_BY?$params"
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }

  private fun getVisitsBySessionTemplateQueryParams(
    sessionTemplateReference: String?,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatus: List<VisitStatus>? = null,
    visitRestrictions: List<VisitRestriction>? = null,
    page: Int? = 0,
    size: Int? = 100,
  ): List<String> {
    val queryParams = ArrayList<String>()
    sessionTemplateReference?.let {
      queryParams.add("sessionTemplateReference=$sessionTemplateReference")
    }
    queryParams.add("fromDate=$fromDate")
    queryParams.add("toDate=$toDate")

    visitStatus?.forEach {
      queryParams.add("visitStatus=$it")
    }
    visitRestrictions?.forEach {
      queryParams.add("visitRestrictions=$it")
    }
    queryParams.add("page=$page")
    queryParams.add("size=$size")
    return queryParams
  }
}
