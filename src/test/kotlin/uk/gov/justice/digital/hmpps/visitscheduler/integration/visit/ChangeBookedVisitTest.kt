package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitChangeUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_CHANGE")
class ChangeBookedVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  lateinit var bookedVisit: Visit
  lateinit var sessionTemplate: SessionTemplate

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  companion object {
    const val actionedByUserName = "user-1"
  }

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    sessionTemplate = sessionTemplateEntityHelper.create()

    val visit = visitEntityHelper.create(visitStatus = BOOKED, sessionTemplateReference = sessionTemplate.reference)

    visitEntityHelper.createNote(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visit, name = "OTHER", details = "Some Text")

    bookedVisit = visitEntityHelper.save(visit)
  }

  private fun createReserveVisitSlotDto(
    prisonerId: String = "FF0000AA",
    startTimestamp: LocalDateTime = bookedVisit.visitStart,
    visitRestriction: VisitRestriction = OPEN,
    sessionTemplateReference: String = "sessionTemplateReference",
  ): ReserveVisitSlotDto {
    return ReserveVisitSlotDto(
      prisonerId = prisonerId,
      startTimestamp = startTimestamp,
      endTimestamp = bookedVisit.visitEnd,
      visitRestriction = visitRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = actionedByUserName,
      sessionTemplateReference = sessionTemplateReference,
    )
  }

  @Test
  fun `change visit has given reference`() {
    // Given
    val reference = bookedVisit.reference
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = bookedVisit.sessionTemplateReference!!)

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    val reservedVisit = testVisitRepository.findByApplicationReference(visit.applicationReference)

    assertThat(reservedVisit).isNotNull
    reservedVisit?.let {
      val visitStartStr = reservedVisit.visitStart.toLocalDate().atTime(sessionTemplate.startTime).format(DateTimeFormatter.ISO_DATE_TIME)

      assertThat(reservedVisit.id).isNotEqualTo(bookedVisit.id)
      assertThat(reservedVisit.visitStatus).isEqualTo(VisitStatus.CHANGING)
      verify(telemetryClient).trackEvent(
        eq("visit-changed"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(reservedVisit.reference)
          assertThat(it["applicationReference"]).isNotEqualTo(bookedVisit.applicationReference)
          assertThat(it["applicationReference"]).isEqualTo(reservedVisit.applicationReference)
          assertThat(it["prisonerId"]).isEqualTo(reservedVisit.prisonerId)
          assertThat(it["prisonId"]).isEqualTo(reservedVisit.prison.code)
          assertThat(it["visitType"]).isEqualTo(reservedVisit.visitType.name)
          assertThat(it["visitRoom"]).isEqualTo(reservedVisit.visitRoom)
          assertThat(it["visitRestriction"]).isEqualTo(reservedVisit.visitRestriction.name)
          assertThat(it["visitStart"]).isEqualTo(visitStartStr)
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.CHANGING.name)
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-changed"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("visit-changed"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
          assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
          assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
          assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
          assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
          assertThat(it["visitStart"]).isEqualTo(visitStartStr)
          assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        },
        isNull(),
      )
    }
  }

  @Test
  fun `changed booked visit creates new visit when prisonId has changed`() {
    // Given
    val reference = bookedVisit.reference
    val sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "NEW")
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference)

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .returnResult()

    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(testVisitRepository.findByApplicationReference(visit.applicationReference)!!.visitStatus).isEqualTo(VisitStatus.RESERVED)

    verify(telemetryClient, times(1)).trackEvent(eq("visit-changed"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-changed"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
  }

  @Test
  fun `changed booked visit creates new visit when prisonerId has changed`() {
    // Given
    val reference = bookedVisit.reference
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference, prisonerId = "NEW" + bookedVisit.prisonerId)

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .returnResult()

    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(testVisitRepository.findByApplicationReference(visit.applicationReference)!!.visitStatus).isEqualTo(VisitStatus.RESERVED)

    verify(telemetryClient, times(1)).trackEvent(eq("visit-changed"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-changed"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
  }

  @Test
  fun `changed booked visit creates new visit when startTimestamp has changed`() {
    // Given
    val reference = bookedVisit.reference
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference, startTimestamp = bookedVisit.visitStart.minusDays(1))

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .returnResult()

    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(testVisitRepository.findByApplicationReference(visit.applicationReference)!!.visitStatus).isEqualTo(VisitStatus.RESERVED)

    verify(telemetryClient, times(1)).trackEvent(eq("visit-changed"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-changed"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
  }

  @Test
  fun `changed booked visit creates new visit when visit restriction has changed`() {
    // Given
    val reference = bookedVisit.reference
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(visitRestriction = CLOSED, sessionTemplateReference = sessionTemplate.reference)

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, reference)

    // Then
    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .returnResult()

    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertThat(testVisitRepository.findByApplicationReference(visit.applicationReference)!!.visitStatus).isEqualTo(VisitStatus.RESERVED)
    verify(telemetryClient, times(1)).trackEvent(eq("visit-changed"), any(), isNull())

    verify(telemetryClient).trackEvent(
      eq("visit-changed"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
  }

  @Test
  fun `change visit - invalid request`() {
    // Given
    val reference = bookedVisit.reference

    // When
    val responseSpec = callVisitChange(webTestClient = webTestClient, authHttpHeaders = roleVisitSchedulerHttpHeaders, reference = reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `change visit - access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference)
    val reference = bookedVisit.reference

    // When
    val responseSpec = callVisitChange(webTestClient, incorrectAuthHeaders, reserveVisitSlotDto, reference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `change visit - unauthorised when no token`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference)

    val jsonBody = BodyInserters.fromValue(reserveVisitSlotDto)
    val reference = bookedVisit.reference

    // When
    val responseSpec = webTestClient.put().uri(getVisitChangeUrl(reference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `change visit - not found`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference)
    val applicationReference = "IM NOT HERE"

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, applicationReference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `change visit that has already expired returns bad request`() {
    // Given
    val visitStart = LocalDateTime.of((LocalDateTime.now().year - 1), 11, 1, 12, 30, 44)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, visitStart = visitStart, reference = "expired-visit-1")

    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplateReference = sessionTemplate.reference)

    // When
    val responseSpec = callVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, expiredVisit.reference)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - ${expiredVisit.reference} is in the past, it cannot be changed")
  }
}
