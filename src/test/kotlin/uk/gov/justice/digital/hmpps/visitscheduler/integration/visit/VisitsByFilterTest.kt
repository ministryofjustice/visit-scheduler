package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CONTROLLER_SEARCH_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.SUPERSEDED_CANCELLATION
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@DisplayName("GET /visits")
class VisitsByFilterTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var visitMin: Visit
  private lateinit var visitFullWithNoVisitors: Visit
  private lateinit var visitFullWithOneVisitor: Visit
  private lateinit var visitFullWithMultipleVisitors: Visit

  @BeforeEach
  internal fun createVisits() {
    visitMin = visitEntityHelper.create(prisonCode = "MDI", visitStart = visitTime, prisonerId = "FF0000AA")

    visitFullWithNoVisitors = visitEntityHelper.create(prisonCode = "LEI", visitStart = visitTime.plusDays(1), prisonerId = "FF0000BB")

    visitEntityHelper.createNote(visit = visitFullWithNoVisitors, text = "A visit concern", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visitFullWithNoVisitors, text = "A visit outcome", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visitFullWithNoVisitors, text = "A visit comment", type = VISIT_COMMENT)
    visitEntityHelper.createNote(visit = visitFullWithNoVisitors, text = "Status has changed", type = STATUS_CHANGED_REASON)
    visitRepository.saveAndFlush(visitFullWithNoVisitors)

    visitFullWithOneVisitor = visitEntityHelper.create(
      prisonCode = "LEI",
      prisonerId = "FF0000CC",
      visitStart = visitTime.plusDays(2),
      visitEnd = visitTime.plusDays(2).plusHours(1),
    )
    visitEntityHelper.createContact(visit = visitFullWithOneVisitor, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createNote(visit = visitFullWithOneVisitor, text = "A visit concern", type = VISITOR_CONCERN)
    visitEntityHelper.createVisitor(visit = visitFullWithOneVisitor, nomisPersonId = 123L, visitContact = true)
    visitEntityHelper.createSupport(visit = visitFullWithOneVisitor, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(visitFullWithOneVisitor)

    visitFullWithMultipleVisitors = visitEntityHelper.create(prisonCode = "LEI", visitStart = visitTime.plusDays(1), prisonerId = "FF0000DD")
    visitEntityHelper.createNote(visit = visitFullWithMultipleVisitors, text = "A visit concern", type = VISITOR_CONCERN)
    visitEntityHelper.createContact(visit = visitFullWithMultipleVisitors, name = "Jane D", phone = "01111 111111")
    visitEntityHelper.createVisitor(visit = visitFullWithMultipleVisitors, nomisPersonId = 222L, visitContact = true)
    visitEntityHelper.createVisitor(visit = visitFullWithMultipleVisitors, nomisPersonId = 444L, visitContact = false)
    visitEntityHelper.createVisitor(visit = visitFullWithMultipleVisitors, nomisPersonId = 666L, visitContact = false)
    visitEntityHelper.createSupport(visit = visitFullWithMultipleVisitors, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(visitFullWithMultipleVisitors)

    visitEntityHelper.create(prisonerId = "GG0000BB", visitStart = visitTime.plusHours(1), visitStatus = RESERVED)
    visitEntityHelper.create(prisonerId = "GG0000BB", visitStart = visitTime.plusDays(1).plusHours(1), visitStatus = BOOKED)
    visitEntityHelper.create(prisonerId = "GG0000BB", visitStart = visitTime.plusDays(2).plusHours(1), visitStatus = CANCELLED)

    visitEntityHelper.create(prisonCode = "AWE", prisonerId = "GG0000BAA", visitStart = visitTime.plusDays(2).plusHours(1), visitStatus = BOOKED)
    visitEntityHelper.create(prisonCode = "AWE", prisonerId = "GG0000BAA", visitStart = visitTime.plusDays(2).plusHours(1), visitStatus = CANCELLED, outcomeStatus = SUPERSEDED_CANCELLATION)
  }

  @Test
  fun `get visit by prisoner ID with no visitors`() {
    // Given
    val prisonerId = "FF0000BB"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("FF0000BB")
      .jsonPath("$.content[0].startTimestamp").isEqualTo(visitFullWithNoVisitors.visitStart.toString())
      .jsonPath("$.content[0].reference").exists()
      .jsonPath("$.content[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$.content[0].visitNotes[?(@.type=='VISIT_COMMENT')].text").isEqualTo("A visit comment")
      .jsonPath("$.content[0].visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("A visit outcome")
      .jsonPath("$.content[0].visitNotes[?(@.type=='STATUS_CHANGED_REASON')].text").isEqualTo("Status has changed")
      .jsonPath("$.content[0].visitors.length()").isEqualTo(0)
      .jsonPath("$.content[0].visitContact").doesNotExist()
      .jsonPath("$.content[0].visitorSupport.length()").isEqualTo(0)
  }

  @Test
  fun `get visit without prisoner ID or prison ID is not allowed`() {
    // Given

    // When
    val responseSpec = callSearchVisitEndPoint("/visits")

    // Then
    responseSpec
      .expectStatus().isBadRequest
  }

  @Test
  fun `get visit by prisoner ID with one visitor`() {
    // Given
    val prisonId = "LEI"
    val prisonerId = "FF0000CC"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("FF0000CC")
      .jsonPath("$.content[0].startTimestamp").isEqualTo(visitFullWithOneVisitor.visitStart.toString())
      .jsonPath("$.content[0].reference").exists()
      .jsonPath("$.content[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$.content[0].visitors.length()").isEqualTo(1)
      .jsonPath("$.content[0].visitContact.name").isEqualTo("Jane Doe")
      .jsonPath("$.content[0].visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `get visit by prisoner ID with multiple visitors`() {
    // Given
    val prisonId = "LEI"
    val prisonerId = "FF0000DD"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("FF0000DD")
      .jsonPath("$.content[0].startTimestamp").isEqualTo(visitFullWithMultipleVisitors.visitStart.toString())
      .jsonPath("$.content[0].reference").exists()
      .jsonPath("$.content[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$.content[0].visitors.length()").isEqualTo(3)
      .jsonPath("$.content[0].visitContact.name").isEqualTo("Jane D")
      .jsonPath("$.content[0].visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `get visit by prison ID`() {
    // Given
    val prisonId = "LEI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$..prisonerId").value(
        Matchers.containsInAnyOrder(
          "FF0000CC",
          "FF0000BB",
          "FF0000DD",
        ),
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
          "LEI",
          "LEI",
          "LEI",
        ),
      )
  }

  @Test
  fun `gets only one visit when visit has been superseded`() {
    // Given
    val prisonId = "AWE"
    val prisonerId = "GG0000BAA"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].visitStatus").isEqualTo("BOOKED")
  }

  @Test
  fun `get visits by prison ID and starting on or after a specified date`() {
    // Given
    val prisonId = "LEI"
    val startDateTime = "2021-11-03T09:00:00"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&startDateTime=$startDateTime&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
  }

  @Test
  fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {
    // Given
    val prisonId = "MDI"
    val startDateTime = "2021-11-01T13:30:45"
    val prisonerId = "GG0000BB"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&prisonerId=$prisonerId&startDateTime=$startDateTime&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-02T13:30:44",
        ),
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
          "MDI",
          "MDI",
        ),
      )
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "GG0000BB",
          "GG0000BB",
        ),
      )
  }

  @Test
  fun `get visits starting before a specified date`() {
    // Given
    val endDateTime = "2021-11-03T09:00:00"
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&endDateTime=$endDateTime&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-01T13:30:44",
          "2021-11-01T12:30:44",
        ),
      )
  }

  @Test
  fun `get visits starting within a date range`() {
    // Given
    val startDateTime = "2021-11-02T09:00:00"
    val endDateTime = "2021-11-03T09:00:00"
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&startDateTime=$startDateTime&endDateTime=$endDateTime&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
        ),
      )
  }

  @Test
  fun `get visits by visitor`() {
    // Given
    val visitorId = 123
    val prisonId = "LEI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&visitorId=$visitorId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("FF0000CC")
  }

  @Test
  fun `get visits by session template reference`() {
    // Given
    val sessionTemplateReference = "sessionTemplateReference"
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&sessionTemplateReference=$sessionTemplateReference&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("GG0000BB")
      .jsonPath("$.content[0].prisonId").isEqualTo("MDI")
  }

  @Test
  fun `get visits by session template reference and session date`() {
    // Given
    val sessionTemplateReference = "sessionTemplateReference"
    val sessionDateWithVisits = "2021-11-02"
    val sessionDateWithoutVisits = "2023-01-01"
    val prisonId = "MDI"

    // When
    var responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&sessionTemplateReference=$sessionTemplateReference&sessionDate=$sessionDateWithVisits&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("GG0000BB")
      .jsonPath("$.content[0].prisonId").isEqualTo("MDI")

    // When
    responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&sessionTemplateReference=$sessionTemplateReference&sessionDate=$sessionDateWithoutVisits&visitStatus=BOOKED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(0)
  }

  @Test
  fun `get visits by status`() {
    // Given
    val visitStatus = BOOKED
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].prisonerId").isEqualTo("GG0000BB")
  }

  @Test
  fun `get visits paged`() {
    // Given
    val size = 3
    val prisonId = "MDI"

    // When
    val responseSpecFirst = callSearchVisitEndPoint("prisonId=$prisonId&visitStatus=BOOKED,CANCELLED,RESERVED&page=0&size=$size")
    val responseSpecLast = callSearchVisitEndPoint("prisonId=$prisonId&visitStatus=BOOKED,CANCELLED,RESERVED&page=1&size=$size")

    // Then - Page 0
    responseSpecFirst.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$.content[0].startTimestamp").isEqualTo("2021-11-03T13:30:44")
      .jsonPath("$.content[0].visitStatus").isEqualTo(CANCELLED.name)
      .jsonPath("$.content[1].startTimestamp").isEqualTo("2021-11-02T13:30:44")
      .jsonPath("$.content[1].visitStatus").isEqualTo(BOOKED.name)
      .jsonPath("$.content[2].startTimestamp").isEqualTo("2021-11-01T13:30:44")
      .jsonPath("$.content[2].visitStatus").isEqualTo(RESERVED.name)

    // And - Page 1
    responseSpecLast.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content[0].startTimestamp").isEqualTo("2021-11-01T12:30:44")
      .jsonPath("$.content[0].visitStatus").isEqualTo(RESERVED.name)
  }

  @Test
  fun `no visits found for prisoner`() {
    // Given
    val prisonerId = 12345
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED,RESERVED")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(0)
  }

  @Test
  fun `get visits - invalid request, contact id should be a long`() {
    // Given
    val nomisPersonId = "123LL"
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `get visits by invalid status`() {
    // Given
    val visitStatus = "AnythingWillDo"
    val prisonId = "MDI"

    // When

    val responseSpec = callSearchVisitEndPoint("prisonId=$prisonId&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonerId = "FF0000AA"
    val prisonId = "MDI"

    // When
    val responseSpec = callSearchVisitEndPoint(params = "prisonId=$prisonId&prisonerId=$prisonerId&visitStatus=BOOKED,CANCELLED,RESERVED", roles = listOf())

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
