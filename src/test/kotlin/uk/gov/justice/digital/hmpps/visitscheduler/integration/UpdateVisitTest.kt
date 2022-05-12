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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitNoteCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

private const val TEST_END_POINT = "/visits/"

@Transactional(propagation = SUPPORTS)
@DisplayName("Update PUT /visits")
class UpdateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  private var visitMin: Visit? = null
  private var visitFull: Visit? = null

  @BeforeEach
  internal fun setUp() {

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    visitMin = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withPrisonId("AAA")
      .withVisitRoom("A1")
      .withVisitStart(visitTime)
      .withVisitEnd(visitTime.plusHours(1))
      .withVisitType(VisitType.SOCIAL)
      .withVisitStatus(VisitStatus.RESERVED)
      .save()

    visitFull = visitCreator(visitRepository)
      .withPrisonerId("FF0000BB")
      .withPrisonId("BBB")
      .withVisitRoom("B1")
      .withVisitStart(visitTime.plusDays(2))
      .withVisitEnd(visitTime.plusDays(2).plusHours(1))
      .withVisitType(VisitType.FAMILY)
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitNoteCreator(visit = visitFull!!, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = visitFull!!, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = visitFull!!, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = visitFull!!, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visitFull!!, nomisPersonId = 321L)
    visitSupportCreator(visit = visitFull!!, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(visitFull!!)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `update visit by reference - add booked details`() {

    // Given

    val updateRequest = UpdateVisitRequestDto(
      prisonerId = "FF0000AB",
      prisonId = "AAB",
      visitRoom = "A2",
      startTimestamp = visitTime.plusDays(2),
      endTimestamp = visitTime.plusDays(2).plusHours(1),
      visitType = VisitType.FAMILY,
      visitStatus = VisitStatus.BOOKED,
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitors = listOf(CreateVisitorOnVisitRequestDto(123L)),
      visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then

    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isNotEmpty
      .jsonPath("$.prisonerId").isEqualTo(updateRequest.prisonerId!!)
      .jsonPath("$.prisonId").isEqualTo(updateRequest.prisonId!!)
      .jsonPath("$.visitRoom").isEqualTo(updateRequest.visitRoom!!)
      .jsonPath("$.startTimestamp").isEqualTo(updateRequest.startTimestamp.toString())
      .jsonPath("$.endTimestamp").isEqualTo(updateRequest.endTimestamp.toString())
      .jsonPath("$.visitType").isEqualTo(updateRequest.visitType!!.name)
      .jsonPath("$.visitStatus").isEqualTo(updateRequest.visitStatus!!.name)
      .jsonPath("$.visitRestriction").isEqualTo(updateRequest.visitRestriction!!.name)
      .jsonPath("$.visitContact.name").isNotEmpty
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!![0].nomisPersonId)
      .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!![0].type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!![0].text!!)
      .jsonPath("$.createdTimestamp").isNotEmpty
  }

  @Test
  fun `put visit by reference - amend contact`() {

    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
  }

  @Test
  fun `put visit by reference - amend visitors`() {

    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitors = listOf(CreateVisitorOnVisitRequestDto(123L)),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!![0].nomisPersonId)
  }

  @Test
  fun `put visit by reference - amend support`() {
    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!![0].type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!![0].text!!)
  }

  @Test
  fun `put visit by reference - not found`() {
    // Given
    val jsonBody = BodyInserters.fromValue(UpdateVisitRequestDto())

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, "IMNOTHERE")

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val jsonBody = BodyInserters.fromValue(UpdateVisitRequestDto())

    // When
    val responseSpec = callUpdateVisit(authHttpHeaders, jsonBody, "12345")

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(UpdateVisitRequestDto())

    // When
    val responseSpec = webTestClient.post().uri("/visits/12345")
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callUpdateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    jsonBody: BodyInserter<*, in ClientHttpRequest>?,
    reference: String
  ): ResponseSpec {
    return webTestClient.put().uri(TEST_END_POINT + reference)
      .headers(authHttpHeaders)
      .body(jsonBody)
      .exchange()
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
