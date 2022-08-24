package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
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
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

private const val TEST_END_POINT = "/visits"

@Transactional(propagation = SUPPORTS)
@DisplayName("POST /visits")
class CreateVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  private fun createVisitRequest(): CreateVisitRequestDto {
    return CreateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )
  }

  @Test
  fun `created visit`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createVisitRequest()
    )

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isNotEmpty
      .jsonPath("$.prisonId").isEqualTo("MDI")
      .jsonPath("$.prisonerId").isEqualTo("FF0000FF")
      .jsonPath("$.visitRoom").isEqualTo("A1")
      .jsonPath("$.visitType").isEqualTo(SOCIAL.name)
      .jsonPath("$.startTimestamp").isEqualTo(visitTime.toString())
      .jsonPath("$.endTimestamp").isEqualTo(visitTime.plusHours(1).toString())
      .jsonPath("$.visitStatus").isEqualTo(RESERVED.name)
      .jsonPath("$.visitRestriction").isEqualTo(OPEN.name)
      .jsonPath("$.visitContact.name").isEqualTo("John Smith")
      .jsonPath("$.visitContact.telephone").isEqualTo("013448811538")
      .jsonPath("$.visitors.length()").isEqualTo(2)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(123)
      .jsonPath("$.visitors[0].visitContact").isEqualTo(true)
      .jsonPath("$.visitors[1].nomisPersonId").isEqualTo(124)
      .jsonPath("$.visitors[1].visitContact").isEqualTo(false)
      .jsonPath("$.visitorSupport.length()").isEqualTo(1)
      .jsonPath("$.visitorSupport[0].type").isEqualTo("OTHER")
      .jsonPath("$.visitorSupport[0].text").isEqualTo("Some Text")
      .jsonPath("$.createdTimestamp").isNotEmpty
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-created"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prisonId)
        Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.toString())
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())
  }

  @Test
  fun `when visit has no visitors then bad request is returned`() {

    // Given
    val createVisitRequest = CreateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitors = setOf(),
      visitRoom = "A1",
      visitContact = ContactDto("John Smith", "01234 567890")
    )

    val jsonBody = BodyInserters.fromValue(createVisitRequest)

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())
  }

  @Test
  fun `created visit - only one visit contact allowed`() {

    // Given

    val createVisitRequest = CreateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitRoom = "A1",
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(nomisPersonId = 123, visitContact = true),
        VisitorDto(nomisPersonId = 124, visitContact = true)
      ),
      visitorSupport = setOf(
        VisitorSupportDto("OTHER", "Some Text")
      )
    )

    val jsonBody = BodyInserters.fromValue(
      createVisitRequest
    )

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `created visit - invalid support`() {

    // Given
    val createVisitRequest = CreateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitRoom = "A1",
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(),
      visitorSupport = setOf(VisitorSupportDto("ANYTHINGWILLDO")),
    )

    val jsonBody = BodyInserters.fromValue(createVisitRequest)

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())
  }

  @Test
  fun `created visit - invalid request`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      mapOf("wrongProperty" to "wrongValue")
    )

    // When
    val responseSpec = webTestClient.post().uri(TEST_END_POINT)
      .headers(roleVisitSchedulerHttpHeaders)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val jsonBody = BodyInserters.fromValue(createVisitRequest())

    // When
    val responseSpec = callCreateVisit(authHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createVisitRequest())

    // When
    val responseSpec = webTestClient.post().uri(TEST_END_POINT)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callCreateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    jsonBody: BodyInserter<*, in ClientHttpRequest>?
  ): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(authHttpHeaders)
      .body(jsonBody)
      .exchange()
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
