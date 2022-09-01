package uk.gov.justice.digital.hmpps.visitscheduler.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

data class PrisonerReleaseDateMessageEvent(
  val eventType: String,
  val occurredAt: ZonedDateTime,
  val description: String? = null,
  val additionalInformation: PrisonerReleaseAdditionalInformation
)

data class PrisonerReleaseAdditionalInformation(
  val nomsNumber: String,
  val reason: String,
  val currentLocation: String,
  val prisonId: String?,
  val details: String?,
  val currentPrisonStatus: String?
)

data class EventType(val Value: String, val Type: String)
data class MessageAttributes(val eventType: EventType)
data class AwsMessage(
  @JsonProperty("Message")
  val message: String,
  @JsonProperty("MessageId")
  val messageId: String,
  @JsonProperty("MessageAttributes")
  val messageAttributes: MessageAttributes
)

@Service
class PrisonerReleaseDateMessageEventListener(
  private val visitService: VisitService,
  private val objectMapper: ObjectMapper,
) {

  @JmsListener(destination = "prisonerreleasequeue", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun processMessage(rawMessage: String?) {
    val awsMessage = objectMapper.readValue(rawMessage, AwsMessage::class.java)
    val prisonerReleaseEventMessage = objectMapper.readValue(awsMessage.message, PrisonerReleaseDateMessageEvent::class.java)
    visitService.handlePrisonerReleaseDateMessageEvent(prisonerReleaseEventMessage)
  }
}
