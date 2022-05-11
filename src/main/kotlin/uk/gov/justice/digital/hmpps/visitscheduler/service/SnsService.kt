package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.function.Supplier

/**
 * SQS Reference
 * https://github.com/ministryofjustice/hmpps-spring-boot-sqs
 */

@Service
class SnsService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper
) {

  private val domaineventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }
  private val domaineventsTopicClient by lazy { domaineventsTopic.snsClient }

  fun LocalDateTime.toOffsetDateFormat(): String =
    atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendVisitBookedEvent(visit: VisitDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_BOOKED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_BOOKED_DESC,
        occurredAt = visit.createdTimestamp.toOffsetDateFormat(),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
        )
      )
    )
  }

  fun sendVisitCancelledEvent(visit: VisitDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_CANCELLED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_CANCELLED_DESC,
        occurredAt = visit.modifiedTimestamp.toOffsetDateFormat(),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference
        )
      )
    )
  }

  private fun publishToDomainEventsTopic(payload: HMPPSDomainEvent) {
    try {
      val result = domaineventsTopicClient.publish(
        PublishRequest(domaineventsTopic.arn, objectMapper.writeValueAsString(payload))
          .withMessageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue().withDataType("String").withStringValue(payload.eventType)
            )
          )
      )

      telemetryClient.trackEvent(
        "visit-scheduler-${payload.eventType}-event",
        mapOf("messageId" to result.messageId, "reference" to payload.additionalInformation.reference),
        null
      )
    } catch (e: Throwable) {
      throw PublishEventException("Failed to publish Event $payload.eventType to $TOPIC_ID", e)
    }
  }

  companion object {
    const val TOPIC_ID = "domainevents"
    const val EVENT_ZONE_ID = "Europe/London"
    const val EVENT_PRISON_VISIT_VERSION = 1
    const val EVENT_PRISON_VISIT_BOOKED = "prison-visit.booked"
    const val EVENT_PRISON_VISIT_BOOKED_DESC = "Prison Visit Booked"
    const val EVENT_PRISON_VISIT_CANCELLED = "prison-visit.cancelled"
    const val EVENT_PRISON_VISIT_CANCELLED_DESC = "Prison Visit Cancelled"
  }
}

internal data class AdditionalInformation(
  val reference: String,
)

internal data class HMPPSDomainEvent(
  val eventType: String,
  val version: Int,
  val description: String,
  val occurredAt: String,
  val prisonerId: String,
  val additionalInformation: AdditionalInformation,
)

class PublishEventException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<PublishEventException> {
  override fun get(): PublishEventException {
    return PublishEventException(message, cause)
  }
}
