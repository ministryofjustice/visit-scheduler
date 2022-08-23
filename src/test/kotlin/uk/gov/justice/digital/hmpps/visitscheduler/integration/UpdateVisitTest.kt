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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
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
class UpdateVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

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
      .withVisitType(VisitType.SOCIAL)
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitNoteCreator(visit = visitFull!!, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = visitFull!!, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = visitFull!!, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = visitFull!!, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visitFull!!, nomisPersonId = 321L, visitContact = true)
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
      visitType = VisitType.SOCIAL,
      visitStatus = VisitStatus.BOOKED,
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then

    val returnResult = responseSpec
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
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!!.first().nomisPersonId)
      .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!!.first().type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!!.first().text!!)
      .jsonPath("$.createdTimestamp").isNotEmpty
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit.booked-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.booked-event"), any(), isNull())
  }

  @Test
  fun `put visit by reference - amend contact`() {

    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `put visit by reference - amend visitors`() {

    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitors = setOf(VisitorDto(123L, visitContact = true)),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!!.first().nomisPersonId)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `put visit by reference - amend support`() {
    // Given

    val updateRequest = UpdateVisitRequestDto(
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val jsonBody = BodyInserters.fromValue(updateRequest)

    // When
    val responseSpec = callUpdateVisit(roleVisitSchedulerHttpHeaders, jsonBody, visitFull!!.reference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!!.first().type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!!.first().text!!)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
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

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
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
