package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class BookVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedVisit: Visit

  @BeforeEach
  internal fun setUp() {

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedVisit = visitEntityHelper.create()

    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = reservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = reservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = reservedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = reservedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(reservedVisit)
  }

  @Test
  fun `Book visit visit by application Reference`() {

    // Given
    val applicationReference = reservedVisit.applicationReference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then

    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reservedVisit.reference)
      .jsonPath("$.applicationReference").isEqualTo(reservedVisit.applicationReference)
      .jsonPath("$.prisonerId").isEqualTo(reservedVisit.prisonerId)
      .jsonPath("$.prisonId").isEqualTo(reservedVisit.prison.code)
      .jsonPath("$.visitRoom").isEqualTo(reservedVisit.visitRoom)
      .jsonPath("$.startTimestamp").isEqualTo(reservedVisit.visitStart.toString())
      .jsonPath("$.endTimestamp").isEqualTo(reservedVisit.visitEnd.toString())
      .jsonPath("$.visitType").isEqualTo(reservedVisit.visitType.name)
      .jsonPath("$.visitStatus").isEqualTo(BOOKED.name)
      .jsonPath("$.visitRestriction").isEqualTo(reservedVisit.visitRestriction.name)
      .jsonPath("$.visitContact.name").isEqualTo(reservedVisit.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(reservedVisit.visitContact!!.telephone)
      .jsonPath("$.visitors.length()").isEqualTo(reservedVisit.visitors.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(reservedVisit.visitors[0].nomisPersonId)
      .jsonPath("$.visitors[0].visitContact").isEqualTo(reservedVisit.visitors[0].visitContact)
      .jsonPath("$.visitorSupport.length()").isEqualTo(reservedVisit.support.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(reservedVisit.support.first().type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(reservedVisit.support.first().text!!)
      .jsonPath("$.createdTimestamp").isNotEmpty
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertBookedEvent(visit, false)
  }

  @Test
  fun `Book visit - Application becomes a Booking when booked - can't be booked twice`() {

    // Given
    val applicationReference = reservedVisit.applicationReference

    // When
    val responseSpecFirstCall = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)
    val responseSpecSecondCall = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpecFirstCall.expectStatus().isOk
    responseSpecSecondCall.expectStatus().isNotFound
  }

  @Test
  fun `Book visit by application Reference - change other visit with same reference to canceled`() {

    // Given
    val reference = reservedVisit.reference

    val bookedVisit = visitEntityHelper.create(visitStatus = BOOKED, reference = reference)

    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = bookedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = bookedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = bookedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(bookedVisit)

    val applicationReference = reservedVisit.applicationReference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then

    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .jsonPath("$.prisonerId").isEqualTo(reservedVisit.prisonerId)
      .returnResult()

    val visits = testVisitRepository.findAllByReference(reference)
    Assertions.assertThat(visits).hasSize(2)
    val bookedEntity = visits.single { it.id == bookedVisit.id }
    val reservedEntity = visits.single { it.id == reservedVisit.id }

    Assertions.assertThat(bookedEntity.visitStatus).isEqualTo(CANCELLED)
    Assertions.assertThat(bookedEntity.outcomeStatus).isEqualTo(OutcomeStatus.SUPERSEDED_CANCELLATION)
    Assertions.assertThat(reservedEntity.visitStatus).isEqualTo(BOOKED)

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertBookedEvent(visit, true)
  }

  @Test
  fun `Book visit visit by application Reference - check order of reserved visits is correct`() {

    // Given
    val reference = reservedVisit.reference

    val newReservedVisit = visitEntityHelper.create(visitStatus = RESERVED, reference = reference, prisonerId = "THIS IS THE ONE")

    visitEntityHelper.createNote(visit = newReservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = newReservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = newReservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = newReservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = newReservedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = newReservedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(newReservedVisit)

    val bookedVisit = visitEntityHelper.create(visitStatus = BOOKED, reference = reference)

    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = bookedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = bookedVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = bookedVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = bookedVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(bookedVisit)

    val applicationReference = newReservedVisit.applicationReference
    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then

    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.prisonerId").isEqualTo("THIS IS THE ONE")
  }

  @Test
  fun `Book visit visit by application Reference - access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val applicationReference = reservedVisit.applicationReference

    // When
    val responseSpec = callVisitBook(webTestClient, authHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Book visit visit by application Reference - unauthorised when no token`() {
    // Given
    val applicationReference = "12345"

    // When
    val responseSpec = webTestClient.post().uri(getVisitBookUrl(applicationReference))
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `Amend and book expired visit - returns bad request error `() {
    val visitStart = LocalDateTime.of((LocalDateTime.now().year - 1), 11, 1, 12, 30, 44)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, reference = "expired-visit", visitStart = visitStart)
    val reservedVisit = visitEntityHelper.create(reference = expiredVisit.reference)

    // Given
    val reference = expiredVisit.reference

    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = expiredVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = expiredVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = expiredVisit, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(expiredVisit)

    val applicationReference = reservedVisit.applicationReference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - $reference is in the past, it cannot be changed")
  }

  private fun assertBookedEvent(visit: VisitDto, isUpdated: Boolean) {
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        Assertions.assertThat(it["isUpdated"]).isEqualTo(isUpdated.toString())
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())
  }
}
