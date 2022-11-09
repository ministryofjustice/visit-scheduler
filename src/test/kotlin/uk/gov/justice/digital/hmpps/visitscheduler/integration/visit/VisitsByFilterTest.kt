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
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
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
      prisonCode = "LEI", prisonerId = "FF0000CC",
      visitStart = visitTime.plusDays(2), visitEnd = visitTime.plusDays(2).plusHours(1)
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
  }

  @Test
  fun `get visit by prisoner ID with no visitors`() {

    // Given
    val prisonerId = "FF0000BB"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonerId=$prisonerId")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000BB")
      .jsonPath("$[0].startTimestamp").isEqualTo(visitFullWithNoVisitors.visitStart.toString())
      .jsonPath("$[0].reference").exists()
      .jsonPath("$[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_COMMENT')].text").isEqualTo("A visit comment")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("A visit outcome")
      .jsonPath("$[0].visitNotes[?(@.type=='STATUS_CHANGED_REASON')].text").isEqualTo("Status has changed")
      .jsonPath("$[0].visitors.length()").isEqualTo(0)
      .jsonPath("$[0].visitContact").doesNotExist()
      .jsonPath("$[0].visitorSupport.length()").isEqualTo(0)
  }

  @Test
  fun `get visit without prisoner ID or prison ID is not allowed`() {

    // Given

    // When
    val responseSpec = callVisitGetEndPoint("/visits")

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
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&prisonerId=$prisonerId")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
      .jsonPath("$[0].startTimestamp").isEqualTo(visitFullWithOneVisitor.visitStart.toString())
      .jsonPath("$[0].reference").exists()
      .jsonPath("$[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$[0].visitors.length()").isEqualTo(1)
      .jsonPath("$[0].visitContact.name").isEqualTo("Jane Doe")
      .jsonPath("$[0].visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `get visit by prisoner ID with multiple visitors`() {

    // Given
    val prisonId = "LEI"
    val prisonerId = "FF0000DD"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&prisonerId=$prisonerId")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000DD")
      .jsonPath("$[0].startTimestamp").isEqualTo(visitFullWithMultipleVisitors.visitStart.toString())
      .jsonPath("$[0].reference").exists()
      .jsonPath("$[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$[0].visitors.length()").isEqualTo(3)
      .jsonPath("$[0].visitContact.name").isEqualTo("Jane D")
      .jsonPath("$[0].visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `get visit by prison ID`() {
    // Given
    val prisonId = "LEI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(3)
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "FF0000CC",
          "FF0000BB",
          "FF0000DD",
        )
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
          "LEI",
          "LEI",
          "LEI"
        )
      )
  }

  @Test
  fun `get visits by prison ID and starting on or after a specified date`() {

    // Given
    val prisonId = "LEI"
    val startTimestamp = "2021-11-03T09:00:00"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&startTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
  }

  @Test
  fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {

    // Given
    val prisonId = "MDI"
    val startTimestamp = "2021-11-01T13:30:45"
    val prisonerId = "GG0000BB"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&prisonerId=$prisonerId&startTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-02T13:30:44",
        )
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
          "MDI",
          "MDI"
        )
      )
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "GG0000BB",
          "GG0000BB"
        )
      )
  }

  @Test
  fun `get visits starting before a specified date`() {
    // Given
    val startTimestamp = "2021-11-03T09:00:00"
    val prisonId = "MDI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&endTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(3)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-01T13:30:44",
          "2021-11-01T12:30:44"
        )
      )
  }

  @Test
  fun `get visits starting within a date range`() {
    // Given
    val startTimestamp = "2021-11-02T09:00:00"
    val endTimestamp = "2021-11-03T09:00:00"
    val prisonId = "MDI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&startTimestamp=$startTimestamp&endTimestamp=$endTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44"
        )
      )
  }

  @Test
  fun `get visits by visitor`() {

    // Given
    val nomisPersonId = 123
    val prisonId = "LEI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
  }

  @Test
  fun `get visits by status`() {
    // Given
    val visitStatus = BOOKED
    val prisonId = "MDI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("GG0000BB")
  }

  @Test
  fun `get visits paged`() {
    // Given
    val size = 3
    val prisonId = "MDI"

    // When
    val responseSpecFirst = callVisitGetEndPoint("/visits?prisonId=$prisonId&page=0&size=$size")
    val responseSpecLast = callVisitGetEndPoint("/visits?prisonId=$prisonId&page=1&size=$size")

    // Then - Page 0
    responseSpecFirst.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(3)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-02T13:30:44",
          "2021-11-01T13:30:44"
        )
      )

    // And - Page 1
    responseSpecLast.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(1)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-01T12:30:44",
        )
      )
  }

  @Test
  fun `no visits found for prisoner`() {
    // Given
    val prisonerId = 12345
    val prisonId = "MDI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&prisonerId=$prisonerId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `get visits - invalid request, contact id should be a long`() {

    // Given
    val nomisPersonId = "123LL"
    val prisonId = "MDI"

    // When
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&nomisPersonId=$nomisPersonId")

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

    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&visitStatus=$visitStatus")

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
    val responseSpec = callVisitGetEndPoint("/visits?prisonId=$prisonId&prisonerId=$prisonerId", listOf())

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
    val responseSpec = webTestClient.get().uri("/visits??prisonId=$prisonId&prisonerId=$prisonerId").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitGetEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
