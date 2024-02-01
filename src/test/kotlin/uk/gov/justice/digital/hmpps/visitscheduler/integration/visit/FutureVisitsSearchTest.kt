package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CONTROLLER_SEARCH_FUTURE_VISITS_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("GET $VISIT_CONTROLLER_SEARCH_FUTURE_VISITS_PATH")
class FutureVisitsSearchTest : IntegrationTestBase() {

  lateinit var sessionTemplateFromNowTimes: SessionTemplate
  lateinit var sessionTemplateBeforeNowTimes: SessionTemplate

  lateinit var beforeNowVisit: Visit
  lateinit var vist1: Visit
  lateinit var vist2: Visit
  lateinit var vist3: Visit

  @BeforeEach
  internal fun createVisits() {
    sessionTemplateBeforeNowTimes = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now().minusDays(1), startTime = LocalTime.now().minusHours(3))
    sessionTemplateFromNowTimes = sessionTemplateEntityHelper.create(validFromDate = LocalDate.now(), startTime = LocalTime.now().plusHours(1))

    beforeNowVisit = createApplicationAndVisit(prisonerId = "FF0000AA", sessionTemplate = sessionTemplateBeforeNowTimes, visitStatus = BOOKED, visitRestriction = VisitRestriction.OPEN)
    vist1 = createApplicationAndVisit(prisonerId = "FF0000AA", sessionTemplate = sessionTemplateFromNowTimes, visitStatus = BOOKED, visitRestriction = VisitRestriction.OPEN)
    vist2 = createApplicationAndVisit(prisonerId = "FF0000AA", sessionTemplate = sessionTemplateFromNowTimes, visitStatus = CANCELLED, visitRestriction = VisitRestriction.OPEN)
    vist3 = createApplicationAndVisit(prisonerId = "GG0000BB", sessionTemplate = sessionTemplateFromNowTimes, visitStatus = BOOKED, visitRestriction = VisitRestriction.OPEN)
  }

  @Test
  fun `Search for future booked visits by prisoner number`() {
    // Given
    val prisonerNumber = vist1.prisonerId

    // When
    val responseSpec = callSearchForFutureVisits(prisonerNumber)

    // Then
    responseSpec.expectStatus().isOk
    val visits = parseVisitsResponse(responseSpec)
    Assertions.assertThat(visits.size).isEqualTo(1)
    Assertions.assertThat(visits[0].reference).isEqualTo(vist1.reference)
  }

  fun callSearchForFutureVisits(
    sessionTemplateReference: String,
    page: Int = 0,
    pageSize: Int = 100,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = VISIT_CONTROLLER_SEARCH_FUTURE_VISITS_PATH.replace("{prisonerNumber}", sessionTemplateReference)
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
