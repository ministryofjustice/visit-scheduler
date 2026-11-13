package uk.gov.justice.digital.hmpps.visitscheduler.integration.events

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.service.HMPPSDomainEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_CHANGED_VISIT
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_BOOKED_DESC
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_CANCELLED_DESC
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_REQUEST_APPROVED
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_PRISON_VISIT_VERSION
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService.Companion.EVENT_ZONE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SendDomainEventTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  private lateinit var snsService: SnsService

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  internal val testQueue by lazy { hmppsQueueService.findByQueueId("domaineventsqueue") ?: throw RuntimeException("Queue with name domaineventstestqueue doesn't exist") }
  internal val testSqsClient by lazy { testQueue.sqsClient }
  internal val testQueueUrl by lazy { testQueue.queueUrl }

  @DisplayName("Publish Domain Event")
  @Nested
  inner class PublishDomainEvent {

    @BeforeEach
    fun `clear queues`() {
      testSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(testQueueUrl).build())
    }

    @ParameterizedTest
    @CsvSource(
      "SOCIAL, prison-visit.booked",
      "SOCIAL_VIDEO, prison-visit.booked",
      "SOCIAL, prison-visit.changed",
      "SOCIAL_VIDEO, prison-visit.changed",
      "SOCIAL, prison-visit.cancelled",
      "SOCIAL_VIDEO, prison-visit.cancelled",
      "SOCIAL, prison-visit-request.approved",
      "SOCIAL_VIDEO, prison-visit-request.approved",
    )
    fun `existing events include the visit type in additional information`(visitType: VisitType, eventType: String) {
      val timestamp = LocalDateTime.of(2026, 10, 26, 10, 0)
      val visit = SnsDomainEventPublishDto(
        reference = "aa-bb-cc-dd",
        createdTimestamp = timestamp,
        modifiedTimestamp = timestamp,
        prisonerId = "A1234BC",
        eventAuditId = 12345,
        visitType = visitType,
      )

      when (eventType) {
        EVENT_PRISON_VISIT_BOOKED -> snsService.sendVisitBookedEvent(visit)
        EVENT_PRISON_CHANGED_VISIT -> snsService.sendChangedVisitBookedEvent(visit)
        EVENT_PRISON_VISIT_CANCELLED -> snsService.sendVisitCancelledEvent(visit)
        EVENT_PRISON_VISIT_REQUEST_APPROVED -> snsService.sendVisitRequestApprovedEvent(visit)
        else -> error("Unexpected event type: $eventType")
      }

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }
      val requestJson = testSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testQueueUrl).build()).join().messages().single().body()
      val (message, attributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      val event = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      val json = objectMapper.readTree(message)

      assertThat(attributes.eventType.Value).isEqualTo(eventType)
      assertThat(event.eventType).isEqualTo(eventType)
      assertThat(event.version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(event.prisonerId).isEqualTo(visit.prisonerId)
      assertThat(event.additionalInformation.reference).isEqualTo(visit.reference)
      assertThat(event.additionalInformation.eventAuditId).isEqualTo(visit.eventAuditId)
      assertThat(event.additionalInformation.visitType).isEqualTo(visitType)
      assertThat(json["additionalInformation"]["visitType"].asString()).isEqualTo(visitType.name)
      assertThat(json.get("visitType")).isNull()
    }

    @Test
    fun `send visit booked event on update`() {
      // Given
      val applicationEntity = createApplicationAndSave(applicationStatus = IN_PROGRESS)
      eventAuditEntityHelper.create(applicationEntity)

      val applicationReference = applicationEntity.reference
      val authHeader = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

      // When
      val responseSpec = callVisitBook(webTestClient, authHeader, applicationReference)

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }

      // Then
      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

      val requestJson = testSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testQueueUrl).build()).join().messages()[0].body()

      val (message, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      assertThat(messageAttributes.eventType.Value).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      val (eventType, version, description, occurredAt, prisonerId, additionalInformation) = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      assertThat(eventType).isEqualTo(EVENT_PRISON_VISIT_BOOKED)
      assertThat(version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(description).isEqualTo(EVENT_PRISON_VISIT_BOOKED_DESC)
      assertThat(ZonedDateTime.parse(occurredAt)).isEqualTo(visit.createdTimestamp.atZone(ZoneId.of(EVENT_ZONE_ID)))
      assertThat(prisonerId).isEqualTo(visit?.prisonerId)
      assertThat(additionalInformation.reference).isEqualTo(visit.reference)
      assertThat(additionalInformation.eventAuditId).isNotNull()
      assertThat(additionalInformation.visitType).isEqualTo(VisitType.SOCIAL)

      // And
      verify(telemetryClient).trackEvent(
        eq("visit-booked"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.BOOKED.name)
          assertThat(it["isUpdated"]).isEqualTo("false")
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("prison-visit.booked-domain-event"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.booked-domain-event"), any(), isNull())
    }

    @Test
    fun `send visit cancelled event`() {
      // Given
      val applicationEntity = createApplicationAndSave(applicationStatus = ACCEPTED)
      val visitEntity = createVisitAndSave(BOOKED, VisitSubStatus.AUTO_APPROVED, applicationEntity)
      val reference = visitEntity.reference
      val authHeader = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
      val cancelVisitDto = CancelVisitDto(
        OutcomeDto(
          OutcomeStatus.PRISONER_CANCELLED,
          "Prisoner got covid",
        ),
        "user-1",
        UserType.STAFF,
        NOT_KNOWN,
      )

      // When
      val responseSpec = callCancelVisit(webTestClient, authHeader, reference, cancelVisitDto)

      await untilCallTo { testQueueEventMessageCount() } matches { it == 1 }

      // Then
      val returnResult = responseSpec.expectStatus().isOk
        .expectBody()
        .returnResult()

      val visit = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)

      val requestJson = testSqsClient.receiveMessage(ReceiveMessageRequest.builder().queueUrl(testQueueUrl).build()).join().messages()[0].body()

      val (message, messageAttributes) = objectMapper.readValue(requestJson, HMPPSMessage::class.java)
      assertThat(messageAttributes.eventType.Value).isEqualTo(EVENT_PRISON_VISIT_CANCELLED)
      val (eventType, version, description, occurredAt, prisonerId, additionalInformation) = objectMapper.readValue(message, HMPPSDomainEvent::class.java)
      assertThat(eventType).isEqualTo(EVENT_PRISON_VISIT_CANCELLED)
      assertThat(version).isEqualTo(EVENT_PRISON_VISIT_VERSION)
      assertThat(description).isEqualTo(EVENT_PRISON_VISIT_CANCELLED_DESC)
      assertThat(ZonedDateTime.parse(occurredAt)).isEqualTo(visit.modifiedTimestamp.atZone(ZoneId.of(EVENT_ZONE_ID)))
      assertThat(prisonerId).isEqualTo(visit.prisonerId)
      assertThat(additionalInformation.reference).isEqualTo(visit.reference)
      assertThat(additionalInformation.eventAuditId).isNotNull()
      assertThat(additionalInformation.visitType).isEqualTo(VisitType.SOCIAL)

      // And
      verify(telemetryClient).trackEvent(
        eq("visit-cancelled"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
          assertThat(it["visitStatus"]).isEqualTo(VisitStatus.CANCELLED.name)
          assertThat(it["outcomeStatus"]).isEqualTo(OutcomeStatus.PRISONER_CANCELLED.name)
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(eq("visit-cancelled"), any(), isNull())

      verify(telemetryClient).trackEvent(
        eq("prison-visit.cancelled-domain-event"),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(visit.reference)
        },
        isNull(),
      )
      verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
    }
  }

  fun testQueueEventMessageCount(): Int? {
    val queueAttributesRequest = GetQueueAttributesRequest.builder().queueUrl(testQueueUrl).attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES).build()
    val queueAttributes = testSqsClient.getQueueAttributes(queueAttributesRequest).join()
    return queueAttributes.attributesAsStrings()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES.toString()]?.toInt()
  }

  data class HMPPSMessage(
    val Message: String,
    val MessageAttributes: HMPPSMessageAttributes,
  )

  data class HMPPSMessageAttributes(val eventType: HMPPSEventType)

  data class HMPPSEventType(val Value: String, val Type: String)
}
