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
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeReservedVisitSlotRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitReserveSlotChangeUrl
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

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_RESERVED_SLOT_CHANGE")
class ChangeReservedSlotTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var visitMin: Visit
  private lateinit var visitFull: Visit

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }

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
      .withVisitStatus(VisitStatus.RESERVED)
      .save()

    visitNoteCreator(visit = visitFull, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitNoteCreator(visit = visitFull, text = "Some text concerns", type = VISITOR_CONCERN)
    visitNoteCreator(visit = visitFull, text = "Some text comment", type = VISIT_COMMENT)
    visitContactCreator(visit = visitFull, name = "Jane Doe", phone = "01234 098765")
    visitVisitorCreator(visit = visitFull, nomisPersonId = 321L, visitContact = true)
    visitSupportCreator(visit = visitFull, name = "OTHER", details = "Some Text")
    visitRepository.saveAndFlush(visitFull)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `change reserved slot by application reference - add final details`() {

    // Given

    val updateRequest = ChangeReservedVisitSlotRequestDto(
      startTimestamp = visitTime.plusDays(2),
      endTimestamp = visitTime.plusDays(2).plusHours(1),
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
      .jsonPath("$.prisonId").isEqualTo(visitFull.prisonId)
      .jsonPath("$.visitRoom").isEqualTo(visitFull.visitRoom)
      .jsonPath("$.startTimestamp").isEqualTo(updateRequest.startTimestamp.toString())
      .jsonPath("$.endTimestamp").isEqualTo(updateRequest.endTimestamp.toString())
      .jsonPath("$.visitType").isEqualTo(visitFull.visitType.name)
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.RESERVED.name)
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
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `change reserved slot by application reference - only one visit contact allowed`() {

    // Given

    val updateRequest = ChangeReservedVisitSlotRequestDto(
      startTimestamp = visitTime.plusDays(2),
      endTimestamp = visitTime.plusDays(2).plusHours(1),
      visitRestriction = VisitRestriction.CLOSED,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = true))
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

    val updateRequest = ChangeReservedVisitSlotRequestDto(
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
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `change reserved slot - amend visitors`() {

    // Given

    val updateRequest = ChangeReservedVisitSlotRequestDto(
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
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `change reserved slot - amend support`() {
    // Given

    val updateRequest = ChangeReservedVisitSlotRequestDto(
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
      eq("visit-scheduler-prison-visit-updated"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())
  }

  @Test
  fun `change reserved slot - not found`() {
    // Given
    val updateRequest = ChangeReservedVisitSlotRequestDto()
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
    val updateRequest = ChangeReservedVisitSlotRequestDto()
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, authHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `change reserved slot - unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(ChangeReservedVisitSlotRequestDto())
    val applicationReference = visitFull.applicationReference

    // When
    val responseSpec = webTestClient.post().uri(getVisitReserveSlotChangeUrl(applicationReference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }
}
