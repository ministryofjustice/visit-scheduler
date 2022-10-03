package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitNoteCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
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

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  private var visitMin: Visit? = null
  private var visitFull: Visit? = null

  @BeforeEach
  internal fun createVisits() {

    visitMin = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStart(visitTime)
      .withPrisonId("MDI")
      .save()

    visitFull = visitCreator(visitRepository)
      .withPrisonerId("FF0000BB")
      .withVisitStart(visitTime.plusDays(1))
      .withVisitEnd(visitTime.plusDays(1).plusHours(1))
      .withPrisonId("LEI")
      .save()

    visitNoteCreator(visit = visitFull!!, text = "A visit concern", type = VISITOR_CONCERN)
    visitNoteCreator(visit = visitFull!!, text = "A visit outcome", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = visitFull!!, text = "A visit comment", type = VISIT_COMMENT)
    visitNoteCreator(visit = visitFull!!, text = "Status has changed", type = STATUS_CHANGED_REASON)

    visitRepository.saveAndFlush(visitFull!!)

    val visitCC = visitCreator(visitRepository)
      .withPrisonerId("FF0000CC")
      .withVisitStart(visitTime.plusDays(2))
      .withVisitEnd(visitTime.plusDays(2).plusHours(1))
      .withPrisonId("LEI")
      .save()
    visitContactCreator(visit = visitCC, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visitCC, nomisPersonId = 123L, visitContact = true)
    visitSupportCreator(visit = visitCC, name = "OTHER", details = "Some Text")

    visitRepository.saveAndFlush(visitCC)

    visitCreator(visitRepository)
      .withPrisonerId("GG0000BB")
      .withVisitStart(visitTime.plusHours(1))
      .withVisitEnd(visitTime.plusHours(2))
      .withPrisonId("BEI")
      .withVisitStatus(VisitStatus.RESERVED)
      .save()

    visitCreator(visitRepository)
      .withPrisonerId("GG0000BB")
      .withVisitStart(visitTime.plusDays(1).plusHours(1))
      .withVisitEnd(visitTime.plusDays(1).plusHours(2))
      .withPrisonId("BEI")
      .withVisitStatus(VisitStatus.BOOKED)
      .save()

    visitCreator(visitRepository)
      .withPrisonerId("GG0000BB")
      .withVisitStart(visitTime.plusDays(2).plusHours(1))
      .withVisitEnd(visitTime.plusDays(2).plusHours(2))
      .withPrisonId("BEI")
      .withVisitStatus(VisitStatus.CANCELLED)
      .save()
  }

  @Test
  fun `get visit by prisoner ID`() {

    // Given
    val prisonerId = "FF0000BB"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000BB")
      .jsonPath("$[0].startTimestamp").isEqualTo(visitFull?.visitStart.toString())
      .jsonPath("$[0].reference").exists()
      .jsonPath("$[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_COMMENT')].text").isEqualTo("A visit comment")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("A visit outcome")
      .jsonPath("$[0].visitNotes[?(@.type=='STATUS_CHANGED_REASON')].text").isEqualTo("Status has changed")
  }

  @Test
  fun `get visit by prison ID`() {
    // Given
    val prisonId = "LEI"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonId=$prisonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "FF0000CC",
          "FF0000BB",
        )
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
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
    val responseSpec = callVisitEndPoint("/visits?prisonId=$prisonId&startTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
  }

  @Test
  fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {

    // Given
    val prisonId = "BEI"
    val startTimestamp = "2021-11-01T13:30:45"
    val prisonerId = "GG0000BB"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId&prisonId=$prisonId&startTimestamp=$startTimestamp")

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
          "BEI",
          "BEI"
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

    // When
    val responseSpec = callVisitEndPoint("/visits?endTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
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

    // When
    val responseSpec = callVisitEndPoint("/visits?startTimestamp=$startTimestamp&endTimestamp=$endTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
        )
      )
  }

  @Test
  fun `get visits by visitor`() {

    // Given
    val nomisPersonId = 123

    // When
    val responseSpec = callVisitEndPoint("/visits?nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
  }

  @Test
  fun `get visits by status`() {
    // Given
    val visitStatus = VisitStatus.BOOKED

    // When
    val responseSpec = callVisitEndPoint("/visits?visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("GG0000BB")
  }

  @Test
  fun `get visits paged`() {
    // Given
    val size = 4

    // When
    val responseSpecFirst = callVisitEndPoint("/visits?page=0&size=$size")
    val responseSpecLast = callVisitEndPoint("/visits?page=1&size=$size")

    // Then - Page 0
    responseSpecFirst.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(4)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-03T12:30:44",
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
        )
      )

    // And - Page 1
    responseSpecLast.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-01T13:30:44",
          "2021-11-01T12:30:44",
        )
      )
  }

  @Test
  fun `no visits found for prisoner`() {
    // Given
    val prisonerId = 12345

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `get visits - invalid request, contact id should be a long`() {

    // Given
    val nomisPersonId = "123LL"

    // When
    val responseSpec = callVisitEndPoint("/visits?nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `get visits by invalid status`() {
    // Given
    val visitStatus = "AnythingWillDo"

    // When

    val responseSpec = callVisitEndPoint("/visits?visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonerId = "FF0000AA"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId", listOf())

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val prisonerId = "FF0000AA"

    // When
    val responseSpec = webTestClient.get().uri("/visits?prisonerId=$prisonerId").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
