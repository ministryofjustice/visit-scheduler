package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import java.time.LocalDate
import java.util.ArrayList

@DisplayName("GET /visits/session-template")
class VisitsBySessionTemplateFilterTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  lateinit var sessionTemplate2: SessionTemplate

  lateinit var visit1: Visit
  lateinit var visit2: Visit
  lateinit var visit3: Visit
  lateinit var visit4: Visit
  lateinit var visit5: Visit
  lateinit var visit6: Visit
  lateinit var visit7: Visit

  @BeforeEach
  internal fun createVisits() {
    sessionTemplate2 = sessionTemplateEntityHelper.create(name = "template-2", prisonCode = "DFT")
    // visit 1 booked for session template reference - session-1
    visit1 = createApplicationAndVisit(slotDate = LocalDate.now(), prisonerId = "FF0000AA", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 2 cancelled for session template reference - session-1
    visit2 = createApplicationAndVisit(slotDate = LocalDate.now(), prisonerId = "FF0000AA", sessionTemplate = sessionTemplateDefault, visitStatus = CANCELLED, visitRestriction = OPEN)
    // visit 3 booked for session template reference - session-2
    visit3 = createApplicationAndVisit(slotDate = LocalDate.now(), prisonerId = "FF0000AA", sessionTemplate = sessionTemplate2, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 4 booked for session template reference - session-1 but on next day
    visit4 = createApplicationAndVisit(slotDate = LocalDate.now().plusDays(1), prisonerId = "FF0000BB", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 5 - closed visit booked for session template reference - session-1 but on next day
    visit5 = createApplicationAndVisit(slotDate = LocalDate.now().plusDays(1), prisonerId = "FF0000BB", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = CLOSED)
    // session template reference is null and status is BOOKED
    visit6 = createApplicationAndVisit(prisonerId = "FF0000BB", visitStatus = BOOKED, slotDate = LocalDate.now().plusDays(1))
    // session template reference is null and status is CANCELLED
    visit7 = createApplicationAndVisit(prisonerId = "FF0000BB", visitStatus = CANCELLED, slotDate = LocalDate.now().plusDays(1))
  }

  @Test
  fun `get booked visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val sessionDate = LocalDate.now()

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit1.reference)
  }

  @Test
  fun `get booked and cancelled visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val sessionDate = LocalDate.now()

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED, CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(2)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit1.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit2.reference)
  }

  @Test
  fun `get booked visits by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = LocalDate.now()
    val toDate = fromDate.plusDays(1)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(BOOKED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(3)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit1.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit4.reference)
    Assertions.assertThat(visits[2].reference).isEqualTo(visit5.reference)
  }

  @Test
  fun `get booked visits with restriction type OPEN by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = LocalDate.now()
    val toDate = fromDate.plusDays(1)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(BOOKED, CANCELLED), visitRestrictions = listOf(OPEN)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(3)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit1.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit2.reference)
    Assertions.assertThat(visits[2].reference).isEqualTo(visit4.reference)
  }

  @Test
  fun `get booked visits with restriction type OPEN or CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = LocalDate.now()
    val toDate = fromDate.plusDays(1)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(BOOKED, CANCELLED), visitRestrictions = listOf(OPEN, CLOSED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(4)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit1.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit2.reference)
    Assertions.assertThat(visits[2].reference).isEqualTo(visit4.reference)
    Assertions.assertThat(visits[3].reference).isEqualTo(visit5.reference)
  }

  @Test
  fun `get booked visits with restriction type CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = LocalDate.now()
    val toDate = fromDate.plusDays(1)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = fromDate, toDate = toDate, visitStatus = listOf(BOOKED), visitRestrictions = listOf(CLOSED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit5.reference)
    Assertions.assertThat(visits[0].visitRestriction).isEqualTo(CLOSED)
  }

  @Test
  fun `when session template reference passed is null only session template reference null records are returned`() {
    // Given
    val fromDate = LocalDate.now()
    val toDate = startDate.plusDays(2)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference = null, fromDate = fromDate, toDate = toDate, visitStatus = listOf(BOOKED, CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(2)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit6.reference)
    Assertions.assertThat(visits[1].reference).isEqualTo(visit7.reference)
  }

  @Test
  fun `when session template reference passed is null only session template reference null records matching status are returned`() {
    // Given
    val fromDate = LocalDate.now()
    val toDate = startDate.plusDays(2)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference = null, fromDate = fromDate, toDate = toDate, visitStatus = listOf(CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(visit7.reference)
  }

  @Test
  fun `when session has no visits for a date no records are returned`() {
    // Given
    val sessionTemplateReference = sessionTemplate2.reference
    val sessionDate = LocalDate.now().plusDays(7)

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED, CANCELLED)).joinToString("&")
    val responseSpec = callVisitsBySessionEndPoint(params)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(0)
  }

  @Test
  fun `access forbidden when no role`() {
    val sessionTemplateReference = sessionTemplateDefault.reference
    val sessionDate = startDate

    // When
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED)).joinToString("&")
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
    val params = getVisitsBySessionTemplateQueryParams(sessionTemplateReference, fromDate = sessionDate, toDate = sessionDate, visitStatus = listOf(BOOKED)).joinToString("&")
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
