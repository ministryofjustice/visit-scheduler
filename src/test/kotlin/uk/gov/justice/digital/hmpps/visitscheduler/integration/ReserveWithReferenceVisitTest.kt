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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_REVISE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitDto
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
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_REVISE")
class ReserveWithReferenceVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  lateinit var bookedVisit: Visit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun setUp() {

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val visit = visitCreator(visitRepository)
      .withPrisonerId("FF0000BB")
      .withPrisonId("BBB")
      .withVisitRoom("B1")
      .withVisitStart(UpdateReservationTest.visitTime.plusDays(2))
      .withVisitEnd(UpdateReservationTest.visitTime.plusDays(2).plusHours(1))
      .withVisitType(SOCIAL)
      .withVisitStatus(VisitStatus.BOOKED)
      .save()

    visitNoteCreator(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = visit, name = "OTHER", details = "Some Text")

    bookedVisit = visitRepository.saveAndFlush(visit)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  private fun createReserveVisitRequest(): ReserveVisitDto {
    return ReserveVisitDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitRestriction = OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )
  }

  @Test
  fun `reserved visit with given reference from another booked visit`() {

    // Given

    val jsonBody = BodyInserters.fromValue(
      createReserveVisitRequest()
    )

    // When
    val responseSpec = callSlotRevise(roleVisitSchedulerHttpHeaders, jsonBody, bookedVisit.reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(bookedVisit.reference)
      .returnResult()

    val reservedVisit = visitRepository.findReservedVisit(bookedVisit.reference)
    val checkBookedVisit = visitRepository.findBookedVisit(bookedVisit.reference)
    Assertions.assertThat(checkBookedVisit?.id).isEqualTo(bookedVisit.id)
    Assertions.assertThat(reservedVisit?.id).isNotEqualTo(bookedVisit.id)

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
  fun `reserved visit has given reference`() {

    // Given
    val reference = bookedVisit.reference

    val jsonBody = BodyInserters.fromValue(
      createReserveVisitRequest()
    )

    // When
    val responseSpec = callSlotRevise(roleVisitSchedulerHttpHeaders, jsonBody, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
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
  fun `created visit - invalid request`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      mapOf("wrongProperty" to "wrongValue")
    )

    val reference = bookedVisit.reference

    // When
    val responseSpec = callSlotRevise(roleVisitSchedulerHttpHeaders, jsonBody, reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val jsonBody = BodyInserters.fromValue(createReserveVisitRequest())
    val reference = bookedVisit.reference

    // When
    val responseSpec = callSlotRevise(incorrectAuthHeaders, jsonBody, reference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createReserveVisitRequest())
    val reference = bookedVisit.reference

    // When
    val responseSpec = webTestClient.put().uri(getSlotReviseUrl(reference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun callSlotRevise(
    authHttpHeaders: (HttpHeaders) -> Unit,
    jsonBody: BodyInserter<*, in ClientHttpRequest>?,
    reference: String
  ): ResponseSpec {

    if (jsonBody == null) {
      return webTestClient.put().uri(getSlotReviseUrl(reference))
        .headers(authHttpHeaders)
        .exchange()
    }

    return webTestClient.put().uri(getSlotReviseUrl(reference))
      .headers(authHttpHeaders)
      .body(jsonBody)
      .exchange()
  }

  private fun getSlotReviseUrl(reference: String): String {
    return VISIT_REVISE.replace("{reference}", reference)
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
