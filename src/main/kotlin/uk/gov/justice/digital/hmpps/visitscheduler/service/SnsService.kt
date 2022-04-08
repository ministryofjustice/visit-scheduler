package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
class SnsService(hmppsQueueService: HmppsQueueService, private val objectMapper: ObjectMapper) {

  private val domaineventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }
  private val domaineventsTopicClient by lazy { domaineventsTopic.snsClient }

  fun LocalDateTime.toOffsetDateFormat(): String =
    atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendVisitBookedEvent(visit: VisitDto) {
    log.debug("Sending visit booked event")
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        description = "Prison Visit Booked",
        eventType = EVENT_PRISON_VISIT_BOOKED,
        version = EVENT_PRISON_VISIT_VERSION,
        occurredAt = LocalDateTime.now().toOffsetDateFormat(),
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
          type = visit.visitType.name,
          prisonerId = visit.prisonerId,
        )
      )
    )
  }

  @Suppress("unused")
  fun sendVisitCancelledEvent(visit: VisitDto) {
    log.debug("Sending visit cancelled event")
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        description = "Prison Visit Cancelled",
        eventType = EVENT_PRISON_VISIT_CANCELLED,
        version = EVENT_PRISON_VISIT_VERSION,
        occurredAt = LocalDateTime.now().toOffsetDateFormat(),
        additionalInformation = AdditionalInformation(
          reference = visit.reference
        )
      )
    )
  }

  private fun publishToDomainEventsTopic(payload: HMPPSDomainEvent) {
    log.debug("Event ${payload.eventType} for id ${payload.additionalInformation.reference}")

    if (!EVENT_ENABLED)
      return

    try {
      domaineventsTopicClient.publish(
        PublishRequest(domaineventsTopic.arn, objectMapper.writeValueAsString(payload))
          .withMessageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue().withDataType("String").withStringValue(payload.eventType)
            )
          ).also { log.info("Published event $payload to outbound topic") }
      )
    } catch (e: Throwable) {
      // TODO: Remove try-catch once e2e tested in DEV env
      // throw PublishEventException("Failed to publish Event $payload.eventType to $TOPIC_ID : ${e.message}")
      log.error("Failed to publish Event $payload.eventType to $TOPIC_ID : ${e.message}")
    }
  }

  companion object {
    const val EVENT_ENABLED = true
    const val TOPIC_ID = "domainevents"
    const val EVENT_ZONE_ID = "Europe/London"
    const val EVENT_PRISON_VISIT_VERSION = 1
    const val EVENT_PRISON_VISIT_BOOKED = "prison-visit.booked"
    const val EVENT_PRISON_VISIT_CANCELLED = "prison-visit.cancelled"

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class AdditionalInformation(
  val reference: String,
  val type: String? = null,
  val prisonerId: String? = null,
)

data class HMPPSDomainEvent(
  val eventType: String,
  val version: Int,
  val description: String,
  val occurredAt: String,
  val additionalInformation: AdditionalInformation,
)

@Suppress("unused")
class PublishEventException(message: String? = null, cause: Throwable? = null) :
  RuntimeException(message, cause),
  Supplier<SupportNotFoundException> {
  override fun get(): SupportNotFoundException {
    return SupportNotFoundException(message, cause)
  }
}
