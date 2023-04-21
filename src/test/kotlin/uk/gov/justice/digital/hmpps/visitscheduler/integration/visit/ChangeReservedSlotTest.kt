package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitReserveSlotChangeUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_RESERVED_SLOT_CHANGE")
class ChangeReservedSlotTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var visitMin: Visit
  private lateinit var visitFull: Visit

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)
  }

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    visitMin = visitEntityHelper.create(visitStatus = RESERVED)
    visitFull = visitEntityHelper.create(visitStatus = RESERVED)

    visitEntityHelper.createNote(visit = visitFull, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visitFull, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visitFull, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visitFull, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visitFull, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visitFull, name = "OTHER", details = "Some Text")
    visitEntityHelper.save(visitFull)
  }

  @Test
  fun `change reserved slot by application reference - add final details`() {
    // Given

    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitFull.visitStart,
      endTimestamp = visitFull.visitEnd,
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(visitFull.reference)
      .jsonPath("$.applicationReference").isEqualTo(applicationReference)
      .jsonPath("$.prisonerId").isEqualTo(visitFull.prisonerId)
      .jsonPath("$.prisonId").isEqualTo(visitFull.prison.code)
      .jsonPath("$.capacityGroup").isEqualTo(visitFull.capacityGroup)
      .jsonPath("$.startTimestamp").isEqualTo(updateRequest.startTimestamp.toString())
      .jsonPath("$.endTimestamp").isEqualTo(updateRequest.endTimestamp.toString())
      .jsonPath("$.visitType").isEqualTo(visitFull.visitType.name)
      .jsonPath("$.visitStatus").isEqualTo(RESERVED.name)
      .jsonPath("$.visitRestriction").isEqualTo(updateRequest.visitRestriction!!.name)
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(123)
      .jsonPath("$.visitors[0].visitContact").isEqualTo(true)
      .jsonPath("$.visitors[1].nomisPersonId").isEqualTo(124)
      .jsonPath("$.visitors[1].visitContact").isEqualTo(false)
      .jsonPath("$.visitorSupport.length()").isEqualTo(updateRequest.visitorSupport!!.size)
      .jsonPath("$.visitorSupport[0].type").isEqualTo(updateRequest.visitorSupport!!.first().type)
      .jsonPath("$.visitorSupport[0].text").isEqualTo(updateRequest.visitorSupport!!.first().text!!)
      .jsonPath("$.createdTimestamp").isNotEmpty
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start date has not changed`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = CHANGING, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitBooked.visitStart,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(CHANGING.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start restriction has not changed`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = CHANGING, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      visitRestriction = visitBooked.visitRestriction,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(CHANGING.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start date has changed`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = CHANGING, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitBooked.visitStart.minusDays(1),
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(RESERVED.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start restriction has changed`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = CHANGING, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(RESERVED.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start date has changed back to match booked slot`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = RESERVED, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitBooked.visitStart,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(CHANGING.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - start restriction has changed back to match booked slot`() {
    // Given
    val visitBooked = visitEntityHelper.create(visitStatus = BOOKED)
    val visitReserved = visitEntityHelper.create(visitStatus = RESERVED, reference = visitBooked.reference)

    val updateRequest = ChangeVisitSlotRequestDto(
      visitRestriction = visitBooked.visitRestriction,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitReserved.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(CHANGING.name)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - only one visit contact allowed`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitTime.plusDays(2),
      endTimestamp = visitTime.plusDays(2).plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = true)),
    )
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when change reserved slot has no visitors then bad request is returned`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitFull.visitStart,
      endTimestamp = visitFull.visitEnd,
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = emptySet(),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when change reserved slot has more than 10 visitors then bad request is returned`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      startTimestamp = visitFull.visitStart,
      endTimestamp = visitFull.visitEnd,
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(1, true), VisitorDto(2, false),
        VisitorDto(3, true), VisitorDto(4, false),
        VisitorDto(5, false), VisitorDto(6, false),
        VisitorDto(7, false), VisitorDto(8, false),
        VisitorDto(9, false), VisitorDto(10, false),
        VisitorDto(11, false), VisitorDto(12, false),
      ),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `change reserved slot - contact`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitContact.name").isEqualTo(updateRequest.visitContact!!.name)
      .jsonPath("$.visitContact.telephone").isEqualTo(updateRequest.visitContact!!.telephone)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot - amend visitors`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      visitors = setOf(VisitorDto(123L, visitContact = true)),
    )

    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitors.length()").isEqualTo(updateRequest.visitors!!.size)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(updateRequest.visitors!!.first().nomisPersonId)
      .returnResult()

    // And
    val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot - amend support`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto(
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

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
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  @Test
  fun `change reserved slot - not found`() {
    // Given
    val updateRequest = ChangeVisitSlotRequestDto()
    val applicationReference = "IM NOT HERE"

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `change reserved slot - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val updateRequest = ChangeVisitSlotRequestDto()
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, authHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `change reserved slot - unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(ChangeVisitSlotRequestDto())
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = webTestClient.post().uri(getVisitReserveSlotChangeUrl(applicationReference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }
}
