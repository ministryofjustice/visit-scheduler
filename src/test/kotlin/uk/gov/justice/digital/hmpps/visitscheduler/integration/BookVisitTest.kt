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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitContactCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitNoteCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitSupportCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class BookVisitTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedVisit: Visit

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }

  @BeforeEach
  internal fun setUp() {

    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedVisit = createVisit()

    visitNoteCreator(visit = reservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = reservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = reservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = reservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = reservedVisit, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = reservedVisit, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(reservedVisit)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `Book visit visit by reference`() {

    // Given
    val reference = reservedVisit.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reference)

    // Then

    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isNotEmpty
      .jsonPath("$.prisonerId").isEqualTo(reservedVisit.prisonerId)
      .jsonPath("$.prisonId").isEqualTo(reservedVisit.prisonId)
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

    assertUpdateEvent(visit)
    assertBookedEvent(visit)
  }

  @Test
  fun `Book visit visit by reference - change other visit with same reference to canceled`() {

    // Given
    val reference = reservedVisit.reference

    val bookedVisit = createVisit(visitStatus = BOOKED, reference = reference)

    visitNoteCreator(visit = bookedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = bookedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = bookedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = bookedVisit, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = bookedVisit, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = bookedVisit, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(bookedVisit)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reference)

    // Then

    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .jsonPath("$.prisonerId").isEqualTo(reservedVisit.prisonerId)
      .returnResult()

    val visits = visitRepository.findAllByReference(reference)
    Assertions.assertThat(visits).hasSize(2)
    Assertions.assertThat(visits[0].visitStatus).isEqualTo(CANCELLED)
    Assertions.assertThat(visits[0].outcomeStatus).isEqualTo(OutcomeStatus.SUPERSEDED_CANCELLATION)
    Assertions.assertThat(visits[1].visitStatus).isEqualTo(BOOKED)

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

    assertChangedVisitEvent(visit)
  }

  @Test
  fun `Book visit visit by reference - check order of reserved visits is correct`() {

    // Given
    val reference = reservedVisit.reference

    val newReservedVisit = createVisit(visitStatus = RESERVED, reference = reference, prisonerId = "THIS IS THE ONE")

    visitNoteCreator(visit = newReservedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = newReservedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = newReservedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = newReservedVisit, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = newReservedVisit, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = newReservedVisit, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(newReservedVisit)

    val bookedVisit = createVisit(visitStatus = BOOKED, reference = reference)

    visitNoteCreator(visit = bookedVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = bookedVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = bookedVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = bookedVisit, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = bookedVisit, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = bookedVisit, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(bookedVisit)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, reference)

    // Then

    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.prisonerId").isEqualTo("THIS IS THE ONE")
  }

  @Test
  fun `Book visit visit by reference - access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val reference = "12345"

    // When
    val responseSpec = callVisitBook(webTestClient, authHttpHeaders, reference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Book visit visit by reference - unauthorised when no token`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = webTestClient.post().uri(getVisitBookUrl(reference))
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun assertChangedVisitEvent(visit: VisitDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit.changed-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.changed-event"), any(), isNull())
  }

  private fun assertBookedEvent(visit: VisitDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit.booked-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.booked-event"), any(), isNull())
  }

  private fun assertUpdateEvent(visit: VisitDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  private fun createVisit(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    prisonId: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44),
    visitEnd: LocalDateTime = visitStart.plusHours(1),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = ""
  ): Visit {

    return visitRepository.saveAndFlush(
      Visit(
        visitStatus = visitStatus,
        prisonerId = prisonerId,
        prisonId = prisonId,
        visitRoom = visitRoom,
        visitStart = visitStart,
        visitEnd = visitEnd,
        visitType = visitType,
        visitRestriction = visitRestriction,
        _reference = reference
      )
    )
  }
}
