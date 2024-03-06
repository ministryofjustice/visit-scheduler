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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISITS_BY_SESSION_TEMPLATE_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate

@DisplayName("GET /visits/{sessionTemplateReference}")
class VisitsBySessionTemplateFilterTest : IntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  lateinit var sessionTemplate2: SessionTemplate

  lateinit var vist1: Visit
  lateinit var vist2: Visit
  lateinit var vist3: Visit
  lateinit var vist4: Visit
  lateinit var vist5: Visit

  @BeforeEach
  internal fun createVisits() {
    sessionTemplate2 = sessionTemplateEntityHelper.create()

    vist1 = createApplicationAndVisit(slotDate = startDate, prisonerId = "FF0000AA", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 1 booked for session template reference - session-1
    vist2 = createApplicationAndVisit(slotDate = startDate, prisonerId = "FF0000AA", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 2 reserved for session template reference - session-1
    vist3 = createApplicationAndVisit(slotDate = startDate, prisonerId = "FF0000AA", sessionTemplate = sessionTemplateDefault, visitStatus = CANCELLED, visitRestriction = OPEN)
    // visit 3 booked for session template reference - session-2
    vist4 = createApplicationAndVisit(slotDate = startDate, prisonerId = "FF0000BB", sessionTemplate = sessionTemplate2, visitStatus = BOOKED, visitRestriction = OPEN)
    // visit 4 booked for session template reference - session-1 but on next day
    vist5 = createApplicationAndVisit(slotDate = startDate.plusDays(1), prisonerId = "FF0000BB", sessionTemplate = sessionTemplateDefault, visitStatus = BOOKED, visitRestriction = CLOSED)
  }

  @Test
  fun `get booked visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val sessionDate = startDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(vist5.reference)
  }

  @Test
  fun `get cancelled visits by session template reference for a single session date`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val sessionDate = startDate

    // When
    val responseSpec = callVisitsBySessionEndPoint(
      sessionTemplateReference,
      "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=CANCELLED",
    )

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(vist3.reference)
  }

  @Test
  fun `get booked visits by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = startDate
    val toDate = fromDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(3)
    Assertions.assertThat(visits[0].visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visits[1].visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visits[2].visitStatus).isEqualTo(BOOKED)
  }

  @Test
  fun `get booked visits with restriction type OPEN by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = startDate
    val toDate = fromDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestrictions=OPEN")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(2)
    Assertions.assertThat(visits[0].visitRestriction).isEqualTo(OPEN)
    Assertions.assertThat(visits[1].visitRestriction).isEqualTo(OPEN)
  }

  @Test
  fun `get booked visits with restriction type OPEN or CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = startDate
    val toDate = fromDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestrictions=OPEN,CLOSED")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(3)
    Assertions.assertThat(visits[0].visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visits[0].visitRestriction).isEqualTo(OPEN)
    Assertions.assertThat(visits[1].visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visits[1].visitRestriction).isEqualTo(OPEN)
    Assertions.assertThat(visits[2].visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visits[2].visitRestriction).isEqualTo(CLOSED)
  }

  @Test
  fun `get booked visits with restriction type CLOSED by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = startDate
    val toDate = fromDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED&visitRestrictions=CLOSED")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(vist5.reference)
    Assertions.assertThat(visits[0].visitRestriction).isEqualTo(CLOSED)
  }

  @Test
  fun `get booked and reserved visits by session template reference for a date range`() {
    // Given
    val sessionTemplateReference = sessionTemplateDefault.reference
    val fromDate = startDate
    val toDate = fromDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$fromDate&toDate=$toDate&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsPageResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(3)
  }

  @Test
  fun `when session has no visits for a date no records are returned`() {
    // Given
    val sessionTemplateReference = sessionTemplate2.reference
    val sessionDate = startDate.plusDays(1)

    // When
    val responseSpec = callVisitsBySessionEndPoint(sessionTemplateReference, "?fromDate=$sessionDate&toDate=$sessionDate&visitStatus=BOOKED")

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
