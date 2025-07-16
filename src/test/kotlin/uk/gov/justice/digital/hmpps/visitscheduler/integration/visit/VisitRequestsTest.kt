package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REQUESTS_VISITS_FOR_PRISON_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitRequestSummaryDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate

@DisplayName("GET $VISIT_REQUESTS_VISITS_FOR_PRISON_PATH")
class VisitRequestsTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val primaryPrisonerId = "AA11BCC"
  val secondaryPrisonerId = "XX11YZZ"
  val prisonCode = "ABC"
  lateinit var prison1: Prison
  lateinit var sessionTemplate1: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate1 = sessionTemplateEntityHelper.create(prison = prison1)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when visit requests exist for a prison then they're returned`() {
    // Given
    val requestVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(requestVisit1, type = EventAuditType.REQUESTED_VISIT, actionedByValue = "user1")

    val requestVisit2 = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(2),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(requestVisit2, type = EventAuditType.REQUESTED_VISIT, actionedByValue = "user2")

    val notRequestVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(notRequestVisit, type = BOOKED_VISIT, actionedByValue = "user3")

    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = primaryPrisonerId, firstName = "John", lastName = "Smith", prisonId = sessionTemplate1.prison.code)

    // When
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId = primaryPrisonerId, prisonerSearchResultDto = prisonerDto)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId = secondaryPrisonerId, prisonerSearchResultDto = prisonerDto)

    val responseSpec = callVisitRequestsForPrison(webTestClient, prisonCode, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val requestVisitsSummaryList = getVisitRequestsForPrisonResult(responseSpec)
    Assertions.assertThat(requestVisitsSummaryList).hasSize(2)
  }

  @Test
  fun `when visit requests exist for a prison but call to get prisoner info fails, then they're returned with placeholders instead of failing`() {
    // Given
    val requestVisit1 = createApplicationAndVisit(
      prisonerId = primaryPrisonerId,
      slotDate = LocalDate.now().plusDays(3),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.REQUESTED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(requestVisit1, type = EventAuditType.REQUESTED_VISIT, actionedByValue = "user1")

    val notRequestVisit = createApplicationAndVisit(
      prisonerId = secondaryPrisonerId,
      slotDate = LocalDate.now().plusDays(4),
      visitStatus = BOOKED,
      visitSubStatus = VisitSubStatus.AUTO_APPROVED,
      sessionTemplate = sessionTemplate1,
    )
    eventAuditEntityHelper.create(notRequestVisit, type = BOOKED_VISIT, actionedByValue = "user3")

    // When
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId = primaryPrisonerId, null)

    val responseSpec = callVisitRequestsForPrison(webTestClient, prisonCode, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val requestVisitsSummaryList = getVisitRequestsForPrisonResult(responseSpec)
    Assertions.assertThat(requestVisitsSummaryList).hasSize(1)

    Assertions.assertThat(requestVisitsSummaryList.first().prisonerName).isEqualTo(requestVisit1.prisonerId)
  }

  @Test
  fun `when no visit requests exist for a prison then empty list returned`() {
    // Given no visits exist

    val responseSpec = callVisitRequestsForPrison(webTestClient, prisonCode, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
    val requestVisitsSummaryList = getVisitRequestsForPrisonResult(responseSpec)
    Assertions.assertThat(requestVisitsSummaryList).hasSize(0)
  }

  fun getVisitRequestsForPrisonResult(responseSpec: ResponseSpec): List<VisitRequestSummaryDto> {
    val responseBody = responseSpec.expectBody().returnResult().responseBody
    return objectMapper.readValue(responseBody, object : TypeReference<List<VisitRequestSummaryDto>>() {})
  }

  fun callVisitRequestsForPrison(
    webTestClient: WebTestClient,
    prisonCode: String,
    authHttpHeaders: (HttpHeaders) -> Unit,
  ): ResponseSpec {
    val url = VISIT_REQUESTS_VISITS_FOR_PRISON_PATH.replace("{prisonCode}", prisonCode)

    return webTestClient.get().uri(url)
      .headers(authHttpHeaders)
      .exchange()
  }
}
