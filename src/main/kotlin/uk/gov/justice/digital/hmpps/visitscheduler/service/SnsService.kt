package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.function.Supplier

/**
 * SQS Reference
 * https://github.com/ministryofjustice/hmpps-spring-boot-sqs
 */

@Service
class SnsService(hmppsQueueService: HmppsQueueService, private val objectMapper: ObjectMapper) {

  private val domaineventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }
  private val domaineventsTopicClient by lazy { domaineventsTopic.snsClient }

  fun sendVisitBookedEvent(visit: VisitDto) {
    log.debug("Sending visit booked event")

    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_BOOKED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_BOOKED_DESC,
        occurredAt = convertToOffsetDateAndTime(visit.createdTimestamp),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
        )
      )
    )
  }

  fun sendVisitCancelledEvent(visit: VisitDto) {
    log.debug("Sending visit cancelled event")
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_CANCELLED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_CANCELLED_DESC,
        occurredAt = convertToOffsetDateAndTime(visit.modifiedTimestamp),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference
        )
      )
    )
  }

  private fun convertToOffsetDateAndTime(localDateTime: LocalDateTime): OffsetDateTime {
    return localDateTime.atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime()
  }

  private fun publishToDomainEventsTopic(eventPayload: HMPPSDomainEvent) {
    log.debug("Entered publishToDomainEventsTopic Event ${eventPayload.eventType} for id ${eventPayload.additionalInformation.reference}")

    try {
      domaineventsTopicClient.publish(
        PublishRequest(domaineventsTopic.arn, objectMapper.writeValueAsString(eventPayload))
          .withMessageAttributes(
            mapOf(
              "eventType" to MessageAttributeValue().withDataType("String").withStringValue(eventPayload.eventType)
            )
          ).also { log.info("Published event $eventPayload to outbound topic") }
      )
    } catch (e: Throwable) {
      // throw PublishEventException("Failed to publish Event $payload.eventType to $TOPIC_ID", e)
      // Note: Silently fail until VB-671 is implemented
      log.debug("Failed to publish Event $eventPayload.eventType to $TOPIC_ID", e)
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

    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

internal data class AdditionalInformation(
  val reference: String,
)

internal data class HMPPSDomainEvent(
  val eventType: String,
  val version: Int,
  val description: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX")
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
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
