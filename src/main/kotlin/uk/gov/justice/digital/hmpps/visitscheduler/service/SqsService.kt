package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents.SessionLocationGroupUpdatedDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents.VisitEventDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents.VisitEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitevents.VisitsEvent
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.*

/**
 * SQS Reference
 * https://github.com/ministryofjustice/hmpps-spring-boot-sqs
 */

@Service
class SqsService(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  @Value("\${hmpps.sqs.queues.prisonvisitsevents.queueName}")
  private val queueName: String,
) {
  companion object {
    private const val PRISON_VISIT_NOTIFICATION_TYPE = "PrisonVisitNotification"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val prisonVisitsQueue by lazy { hmppsQueueService.findByQueueName(queueName) ?: throw RuntimeException("Queue with name $queueName doesn't exist") }
  private val prisonVisitsSqsClient by lazy { prisonVisitsQueue.sqsClient }
  private val prisonVisitsQueueUrl by lazy { prisonVisitsQueue.queueUrl }

  fun sendVisitEventToPrisonVisitsQueue(visitEventDto: VisitEventDto) {
    log.debug("Entered : sendVisitEventToPrisonVisitsQueue, message - {}", visitEventDto)

    try {
      val visitEvent = getVisitEvent(visitEventDto)
      if (visitEvent == null) {
        log.error("Unexpected visit event : {}", visitEventDto)
        return
      }

      log.debug("Sending visit event to queue {}", visitEvent)
      val sqsMessage = getSqsMessage(visitEvent)
      log.debug("prison visit event sent - {} ", sqsMessage)

      // drop the message on the prison visits event queue
      prisonVisitsSqsClient.sendMessage(
        SendMessageRequest.builder()
          .queueUrl(prisonVisitsQueueUrl)
          .messageBody(objectMapper.writeValueAsString(sqsMessage))
          .build(),
      )
    } catch (e: Throwable) {
      val message = "Failed to sendMessageToVisitsQueue "
      log.error(message, e)
      throw PublishEventException(message, e)
    }
  }

  private fun getVisitEvent(visitEventDto: VisitEventDto): VisitsEvent? {
    return when (visitEventDto) {
      is SessionLocationGroupUpdatedDto -> VisitsEvent(
        VisitEventType.LOCATION_GROUP_UPDATED.eventType,
        objectMapper.writeValueAsString(visitEventDto),
      )

      else -> {
        null
      }
    }
  }

  private fun getSqsMessage(visitsEvent: VisitsEvent): SQSMessage {
    return SQSMessage(type = PRISON_VISIT_NOTIFICATION_TYPE, message = objectMapper.writeValueAsString(visitsEvent), UUID.randomUUID().toString())
  }
}

internal data class SQSMessage(
  @JsonProperty("Type")
  val type: String,
  @JsonProperty("Message")
  val message: String,
  @JsonProperty("MessageId")
  val messageId: String? = null,
)
