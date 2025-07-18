package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CONTROLLER_SEARCH_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitAssertHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.util.*
import java.util.stream.Collectors

@DisplayName("GET /visits")
class VisitsByFilterTest : IntegrationTestBase() {

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var visitAssertHelper: VisitAssertHelper

  private lateinit var visit: Visit
  private lateinit var otherSessionTemplate: SessionTemplate
  private lateinit var visitInDifferentPrison: Visit
  private lateinit var visitCancelled: Visit

  @BeforeEach
  internal fun createVisits() {
    otherSessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "AWE")

    visitInDifferentPrison = createApplicationAndVisit(prisonerId = "AA0000A", slotDate = startDate.plusWeeks(1), sessionTemplate = otherSessionTemplate)
    visitEntityHelper.createNote(visit = visitInDifferentPrison, text = "A visit concern", type = VISITOR_CONCERN)
    visitEntityHelper.save(visitInDifferentPrison)

    visit = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = startDate.plusDays(2), sessionTemplate = sessionTemplateDefault)
    visitEntityHelper.createNote(visit = visit, text = "A visit concern", type = VISITOR_CONCERN)
    visitEntityHelper.save(visit)

    visitCancelled = createApplicationAndVisit(prisonerId = "FF0000CC", slotDate = startDate.plusDays(3), sessionTemplate = sessionTemplateDefault, visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.CANCELLED)
    visitEntityHelper.createNote(visit = visitCancelled, text = "A visit concern", type = VISITOR_CONCERN)
    visitCancelled.outcomeStatus = OutcomeStatus.CANCELLATION

    visitEntityHelper.save(visitCancelled)
  }

  private val allVisitStatuses = Arrays.stream(VisitStatus.entries.toTypedArray()).map { it.name }.collect(Collectors.joining(","))

  @Test
  fun `get visit by prisoner ID`() {
    // Given
    val prisonerId = visit.prisonerId

    // When
    val responseSpec = callSearchVisitEndPoint("prisonerId=$prisonerId&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visit.prisonerId).isEqualTo(visit.prisonerId)
    Assertions.assertThat(visitList.size).isEqualTo(2)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
    visitAssertHelper.assertVisitDto(visitList[1], visit)
  }

  @Test
  fun `get visit by prison`() {
    // Given
    val prisonCode = visitInDifferentPrison.prison.code

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visitInDifferentPrison)
  }

  @Test
  fun `get visits by prison ID and starting on or after a specified date`() {
    // Given
    val prisonCode = visitCancelled.prison.code
    val visitStartDate = formatDateToString(visitCancelled.sessionSlot.slotDate)
    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStartDate=$visitStartDate&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
  }

  @Test
  fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {
    // Given
    val prisonCode = visitCancelled.prison.code
    val visitStartDate = formatDateToString(visitCancelled.sessionSlot.slotDate)
    val prisonerId = visitCancelled.prisonerId

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&prisonerId=$prisonerId&visitStartDate=$visitStartDate&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
  }

  @Test
  fun `get visits starting before a specified date`() {
    // Given
    val prisonCode = visit.prison.code
    val endDateTime = formatDateToString(visitCancelled.sessionSlot.slotDate)

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&endDateTime=$endDateTime&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(2)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
    visitAssertHelper.assertVisitDto(visitList[1], visit)
  }

  @Test
  fun `get visits starting within a date range`() {
    // Given
    val prisonCode = visit.prison.code
    val visitStartDate = formatDateToString(visit.sessionSlot.slotDate)
    val endDateTime = formatDateToString(visitCancelled.sessionSlot.slotDate)

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStartDate=$visitStartDate&endDateTime=$endDateTime&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(2)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
    visitAssertHelper.assertVisitDto(visitList[1], visit)
  }

  @Test
  fun `get visits by booked status`() {
    // Given
    val visitStatus = BOOKED
    val prisonCode = visit.prison.code
    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visit)
  }

  @Test
  fun `get visits by cancelled status`() {
    // Given
    val visitStatus = CANCELLED
    val prisonCode = visit.prison.code
    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
    val visitList = parseVisitsPageResponse(responseSpec)

    Assertions.assertThat(visitList.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitList[0], visitCancelled)
  }

  @Test
  fun `get visits paged`() {
    // Given
    val size = 1
    val prisonCode = visit.prison.code

    // When
    val responsePageOne = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$allVisitStatuses&page=0&size=$size")
    val responsePageTwo = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$allVisitStatuses&page=1&size=$size")

    // Then - Page 0

    responsePageOne.expectStatus().isOk
    val visitListPageOne = parseVisitsPageResponse(responsePageOne)
    Assertions.assertThat(visitListPageOne.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitListPageOne[0], visitCancelled)

    // And - Page 1
    responsePageTwo.expectStatus().isOk
    val visitListPageTwo = parseVisitsPageResponse(responsePageTwo)
    Assertions.assertThat(visitListPageTwo.size).isEqualTo(1)
    visitAssertHelper.assertVisitDto(visitListPageTwo[0], visit)
  }

  @Test
  fun `get visit without prisoner ID or prison ID is not allowed`() {
    // Given

    // When
    val responseSpec = callSearchVisitEndPoint("visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage").value(Matchers.containsString("Must have prisonId or prisonerId"))
  }

  @Test
  fun `no visits found for prisoner`() {
    // Given
    val prisonerId = 12345
    val prisonCode = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&prisonerId=$prisonerId&visitStatus=$allVisitStatuses")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(0)
  }

  @Test
  fun `get visits by invalid status`() {
    // Given
    val visitStatus = "AnythingWillDo"
    val prisonCode = "MDI"

    // When

    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonCode&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonerId = visit.prisonerId
    val prisonCode = visit.prison.code

    // When
    val responseSpec = callSearchVisitEndPoint(params = "prisonId=$prisonCode&prisonerId=$prisonerId&visitStatus=$allVisitStatuses", roles = listOf())

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val prisonerId = visit.prisonerId
    val prisonCode = visit.prison.code

    // When
    val responseSpec = webTestClient.get().uri("prisonId=$prisonCode&prisonerId=$prisonerId&visitStatus=$allVisitStatuses").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callSearchVisitEndPoint(
    params: String,
    page: Int = 0,
    pageSize: Int = 100,
    roles: List<String> = listOf("ROLE_VISIT_SCHEDULER"),
  ): ResponseSpec {
    val uri = "$VISIT_CONTROLLER_SEARCH_PATH?$params&page=$page&size=$pageSize"
    return webTestClient.get().uri(uri)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
