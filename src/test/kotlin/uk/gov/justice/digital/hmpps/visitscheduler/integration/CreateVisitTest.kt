package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSupportOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

private const val TEST_END_POINT = "/visits"

@Transactional(propagation = SUPPORTS)
@DisplayName("POST /visits")
class CreateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

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
      visitContact = CreateContactOnVisitRequestDto("John Smith", "013448811538"),
      visitors = setOf(CreateVisitorOnVisitRequestDto(123)),
      visitorSupport = setOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
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
    responseSpec.expectStatus().isCreated
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
      .jsonPath("$.visitContact.name").isNotEmpty
      .jsonPath("$.visitContact.name").isEqualTo("John Smith")
      .jsonPath("$.visitContact.telephone").isEqualTo("013448811538")
      .jsonPath("$.visitors.length()").isEqualTo(1)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(123)
      .jsonPath("$.visitorSupport.length()").isEqualTo(1)
      .jsonPath("$.visitorSupport[0].type").isEqualTo("OTHER")
      .jsonPath("$.visitorSupport[0].text").isEqualTo("Some Text")
      .jsonPath("$.createdTimestamp").isNotEmpty
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
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890")
    )

    val jsonBody = BodyInserters.fromValue(createVisitRequest)

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `created visit - duplicates are ignored`() {

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
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitors = setOf(
        CreateVisitorOnVisitRequestDto(123),
        CreateVisitorOnVisitRequestDto(123)
      ),
      visitorSupport = setOf(
        CreateSupportOnVisitRequestDto("OTHER", "Some Text"),
        CreateSupportOnVisitRequestDto("OTHER", "Some Text")
      )
    )

    val jsonBody = BodyInserters.fromValue(
      createVisitRequest
    )

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.visitors.length()").isEqualTo(1)
      .jsonPath("$.visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `created visit - invalid support`() {

    // Given
    val migratedVisitRequestDto = CreateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = SOCIAL,
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitRoom = "A1",
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitors = setOf(),
      visitorSupport = setOf(CreateSupportOnVisitRequestDto("ANYTHINGWILLDO")),
    )

    val jsonBody = BodyInserters.fromValue(migratedVisitRequestDto)

    // When
    val responseSpec = callCreateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest
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
