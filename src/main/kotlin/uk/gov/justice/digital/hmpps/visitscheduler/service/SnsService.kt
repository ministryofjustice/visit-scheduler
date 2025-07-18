package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.publish
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
  private val objectMapper: ObjectMapper,
  @Value("\${feature.events.sns.enabled::true}")
  private val snsEventsEnabled: Boolean,
) {

  companion object {
    const val TOPIC_ID = "domainevents"
    const val EVENT_ZONE_ID = "Europe/London"
    const val EVENT_PRISON_VISIT_VERSION = 1
    const val EVENT_PRISON_VISIT_BOOKED = "prison-visit.booked"
    const val EVENT_PRISON_VISIT_BOOKED_DESC = "Prison Visit Booked"
    const val EVENT_PRISON_CHANGED_VISIT = "prison-visit.changed"
    const val EVENT_PRISON_CHANGED_VISIT_DESC = "Prison Visit Changed"
    const val EVENT_PRISON_VISIT_CANCELLED = "prison-visit.cancelled"
    const val EVENT_PRISON_VISIT_CANCELLED_DESC = "Prison Visit Cancelled"
    const val EVENT_PRISON_VISIT_REQUEST_ACTIONED = "prison-visit-request.actioned"
    const val EVENT_PRISON_VISIT_REQUEST_DESC = "Prison visit request approved or denied"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val domaineventsTopic by lazy { hmppsQueueService.findByTopicId(TOPIC_ID) ?: throw RuntimeException("Topic with name $TOPIC_ID doesn't exist") }
  private val domaineventsTopicClient by lazy { domaineventsTopic.snsClient }

  fun LocalDateTime.toOffsetDateFormat(): String = atZone(ZoneId.of(EVENT_ZONE_ID)).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendVisitBookedEvent(visit: SnsDomainEventPublishDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_BOOKED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_BOOKED_DESC,
        occurredAt = visit.createdTimestamp.toOffsetDateFormat(),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
          eventAuditId = visit.eventAuditId,
        ),
      ),
    )
  }

  fun sendVisitCancelledEvent(visit: SnsDomainEventPublishDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_CANCELLED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_CANCELLED_DESC,
        occurredAt = visit.modifiedTimestamp.toOffsetDateFormat(),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
          eventAuditId = visit.eventAuditId,
        ),
      ),
    )
  }

  private fun publishToDomainEventsTopic(payloadEvent: HMPPSDomainEvent) {
    if (!snsEventsEnabled) {
      log.info("Publish to domain events topic Disabled : {payloadEvent}")
      return
    }
    log.debug("Entered : publishToDomainEventsTopic $payloadEvent")

    try {
      val result = domaineventsTopic.publish(
        payloadEvent.eventType,
        objectMapper.writeValueAsString(payloadEvent),
      )

      telemetryClient.trackEvent(
        "${payloadEvent.eventType}-domain-event",
        mapOf("messageId" to result.messageId(), "reference" to payloadEvent.additionalInformation.reference),
        null,
      )
    } catch (e: Throwable) {
      val message = "Failed (publishToDomainEventsTopic) to publish Event $payloadEvent.eventType to $TOPIC_ID"
      log.error(message, e)
      throw PublishEventException(message, e)
    }
  }

  fun sendChangedVisitBookedEvent(visit: SnsDomainEventPublishDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_CHANGED_VISIT,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_CHANGED_VISIT_DESC,
        occurredAt = visit.createdTimestamp.toOffsetDateFormat(),
        prisonerId = visit.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = visit.reference,
          eventAuditId = visit.eventAuditId,
        ),
      ),
    )
  }

  fun sendVisitRequestActionedEvent(details: SnsDomainEventPublishDto) {
    publishToDomainEventsTopic(
      HMPPSDomainEvent(
        eventType = EVENT_PRISON_VISIT_REQUEST_ACTIONED,
        version = EVENT_PRISON_VISIT_VERSION,
        description = EVENT_PRISON_VISIT_REQUEST_DESC,
        occurredAt = details.createdTimestamp.toOffsetDateFormat(),
        prisonerId = details.prisonerId,
        additionalInformation = AdditionalInformation(
          reference = details.reference,
          eventAuditId = details.eventAuditId,
        ),
      ),
    )
  }
}

internal data class AdditionalInformation(
  val reference: String,
  val eventAuditId: Long,
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
  override fun get(): PublishEventException = PublishEventException(message, cause)
}
