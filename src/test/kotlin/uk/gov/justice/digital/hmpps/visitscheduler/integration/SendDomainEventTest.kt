package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSupportOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_BOOKED_DESC
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_CANCELLED_DESC
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_VERSION
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_ZONE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.ZoneId
import java.time.ZonedDateTime

class SendDomainEventTest(@Autowired private val objectMapper: ObjectMapper) : IntegrationTestBase() {

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  internal val testQueue by lazy { hmppsQueueService.findByQueueId("domaineventsqueue") ?: throw RuntimeException("Queue with name domaineventstestqueue doesn't exist") }
  internal val testSqsClient by lazy { testQueue.sqsClient }
  internal val testQueueUrl by lazy { testQueue.queueUrl }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @DisplayName("Publish Domain Event")
  @Nested
  inner class PublishDomainEvent {

    @BeforeEach
    fun `clear queues`() {
      testSqsClient.purgeQueue(PurgeQueueRequest(testQueueUrl))
    }

    private fun createVisitRequest(): CreateVisitRequestDto {
      return CreateVisitRequestDto(
        prisonId = "MDI",
        prisonerId = "FF0000FF",
        visitRoom = "A1",
        visitType = VisitType.SOCIAL,
        startTimestamp = VisitControllerTest.visitTime,
        endTimestamp = VisitControllerTest.visitTime.plusHours(1),
        visitStatus = VisitStatus.BOOKED,
        visitRestriction = VisitRestriction.OPEN,
        visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
        visitors = setOf(CreateVisitorOnVisitRequestDto(123)),
        visitorSupport = setOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text"))
      )
    }

    private fun createVisitAndSave(visitStatus: VisitStatus): Visit {
      val visit = visitCreator(visitRepository)
        .withVisitStatus(visitStatus)
        .save()
      visitRepository.saveAndFlush(visit)
      return visit
    }

    @Test
    fun `send visit booked event on create`() {

      // Given
      val requestDto = createVisitRequest()

      // When
      val responseSpec = webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(requestDto)
        )
        .exchange()

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }

      // Then
      val returnResult = responseSpec.expectStatus().isCreated
        .expectBody()
        .returnResult()

      val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
      val requestJson = testSqsClient.receiveMessage(testQueueUrl).messages[0].body
      val (message, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      assertThat(messageAttributes.eventType.Value).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      val (eventType, version, description, occurredAt, prisonerId, additionalInformation) = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      assertThat(eventType).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      assertThat(version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(description).isEqualTo(EVENT_PRISON_VISIT_BOOKED_DESC)
      assertThat(ZonedDateTime.parse(occurredAt)).isEqualTo(visit.createdTimestamp.atZone(ZoneId.of(EVENT_ZONE_ID)))
      assertThat(prisonerId).isEqualTo(visit?.prisonerId)
      assertThat(additionalInformation.reference).isEqualTo(visit.reference)

      // And
      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit-created"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["prisonerId"]).isEqualTo("FF0000FF")
          assertThat(it["prisonId"]).isEqualTo("MDI")
          assertThat(it["visitType"]).isEqualTo(VisitType.SOCIAL.name)
          assertThat(it["visitRoom"]).isEqualTo("A1")
          assertThat(it["visitRestriction"]).isEqualTo(VisitRestriction.OPEN.name)
          assertThat(it["visitStart"]).isEqualTo(MigrateVisitTest.visitTime.toString())
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.BOOKED.name)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-created"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit.booked-event"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.booked-event"), any(), isNull())
    }

    @Test
    fun `send visit booked event on update`() {

      // Given
      val visitEntity = createVisitAndSave(VisitStatus.RESERVED)

      // When
      val responseSpec = webTestClient.put().uri("/visits/${visitEntity.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(UpdateVisitRequestDto(visitStatus = VisitStatus.BOOKED))
        )
        .exchange()

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }

      // Then
      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

      val requestJson = testSqsClient.receiveMessage(testQueueUrl).messages[0].body
      val (message, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      assertThat(messageAttributes.eventType.Value).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      val (eventType, version, description, occurredAt, prisonerId, additionalInformation) = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      assertThat(eventType).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      assertThat(version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(description).isEqualTo(EVENT_PRISON_VISIT_BOOKED_DESC)
      assertThat(ZonedDateTime.parse(occurredAt)).isEqualTo(visit.createdTimestamp.atZone(ZoneId.of(EVENT_ZONE_ID)))
      assertThat(prisonerId).isEqualTo(visit?.prisonerId)
      assertThat(additionalInformation.reference).isEqualTo(visit.reference)

      // And
      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit-updated"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.BOOKED.name)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-updated"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit.booked-event"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.booked-event"), any(), isNull())
    }

    @Test
    fun `send visit cancelled event`() {
      // Given
      val visitEntity = createVisitAndSave(VisitStatus.BOOKED)

      // When
      val responseSpec = webTestClient.patch().uri("/visits/${visitEntity.reference}/cancel")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(OutcomeDto(OutcomeStatus.PRISONER_CANCELLED, "AnyThingWillDo"))
        )
        .exchange()

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }

      // Then
      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
      val requestJson = testSqsClient.receiveMessage(testQueueUrl).messages[0].body
      val (message, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      assertThat(messageAttributes.eventType.Value).isEqualTo(EVENT_PRISON_VISIT_CANCELLED)
      val (eventType, version, description, occurredAt, prisonerId, additionalInformation) = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      assertThat(eventType).isEqualTo(EVENT_PRISON_VISIT_CANCELLED)
      assertThat(version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(description).isEqualTo(EVENT_PRISON_VISIT_CANCELLED_DESC)
      assertThat(ZonedDateTime.parse(occurredAt)).isEqualTo(visit.modifiedTimestamp.atZone(ZoneId.of(EVENT_ZONE_ID)))
      assertThat(prisonerId).isEqualTo(visit.prisonerId)
      assertThat(additionalInformation.reference).isEqualTo(visit.reference)

      // And
      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit-cancelled"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.CANCELLED.name)
          assertThat(it["outcomeStatus"]).isEqualTo(OutcomeStatus.PRISONER_CANCELLED.name)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-cancelled"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("visit-scheduler-prison-visit.cancelled-event"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
        },
        isNull()
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit.cancelled-event"), any(), isNull())
    }
  }

  fun testQueueEventMessageCount(): Int? {
    val queueAttributes = testSqsClient.getQueueAttributes(testQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }

  data class HMPPSMessage(
    val Message: String,
    val MessageAttributes: HMPPSMessageAttributes
  )

  data class HMPPSMessageAttributes(val eventType: HMPPSEventType)

  data class HMPPSEventType(val Value: String, val Type: String)
}
