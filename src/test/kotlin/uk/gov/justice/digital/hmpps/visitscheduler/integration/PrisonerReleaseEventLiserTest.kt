package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.PublishResult
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerReleaseAdditionalInformation
import uk.gov.justice.digital.hmpps.visitscheduler.service.PrisonerReleaseDateMessageEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitService
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import java.time.ZonedDateTime

class PrisonerReleaseEventLiserTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService
  protected val mapper: ObjectMapper = ObjectMapper()

  @SpyBean
  protected lateinit var visitServiceSpy: VisitService

  private val prisonerReleaseTopic by lazy { hmppsQueueService.findByTopicId("prisonerreleasetopic") ?: throw MissingQueueException("Prisonerreleasetopic not found") }
  private val prisonerReleaseQueue by lazy { hmppsQueueService.findByQueueId("prisonerreleasequeue") ?: throw MissingQueueException("Prisonerreleasequeue not found") }
  private val prisonerReleaseSqsClient by lazy { prisonerReleaseQueue.sqsClient }
  protected val prisonerReleaseQueueUrl by lazy { prisonerReleaseQueue.queueUrl }
  protected val prisonerReleaseDlqQueueUrl by lazy { prisonerReleaseQueue.dlqUrl as String }

  internal fun AmazonSQS.countMessagesOnQueue(queueUrl: String): Int {

    val attributeKeys = listOf("ApproximateNumberOfMessages")
    val queueAttributesResult = this.getQueueAttributes(queueUrl, attributeKeys)
    return queueAttributesResult.let {
      it.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0
    }
  }

  @BeforeEach
  fun `clear prisoner release queues`() {
    mapper.registerModule(JavaTimeModule())
    prisonerReleaseSqsClient.purgeQueue(PurgeQueueRequest(prisonerReleaseQueueUrl))
    prisonerReleaseSqsClient.purgeQueue(PurgeQueueRequest(prisonerReleaseDlqQueueUrl))
  }

  @Test
  fun `event is published to prisoner release topic`() {

    // Given
    val eventAdditionalInformation = PrisonerReleaseAdditionalInformation(
      nomsNumber = "THIS_HAS_WORKED",
      reason = "RELEASED",
      details = "Movement reason code CR",
      currentLocation = "OUTSIDE_PRISON",
      prisonId = "MDI",
      currentPrisonStatus = "NOT_UNDER_PRISON_CARE"
    )

    val event = PrisonerReleaseDateMessageEvent(
      "prison-offender-events.prisoner.released",
      ZonedDateTime.now(),
      "some event contents",
      eventAdditionalInformation
    )

    // When
    val result = publishToTopic(event)

    // Then
    assertThat(result.sdkHttpMetadata.httpStatusCode).isEqualTo(200)
    assertThat(result.messageId).isNotNull()
    assertHasNoDlqMessages()
    assertPrisonerReleaseQueueHasProcessedMessages()

    val captor = argumentCaptor<PrisonerReleaseDateMessageEvent>()
    verify(visitServiceSpy).handlePrisonerReleaseDateMessageEvent(captor.capture())

    assertThat(captor.firstValue.eventType).isEqualTo("prison-offender-events.prisoner.released")
    // Need to check this base on epoch as the Json converter changes the time zone to UTC
    assertThat(captor.firstValue.occurredAt.toEpochSecond()).isEqualTo(event.occurredAt.toEpochSecond())
    assertThat(captor.firstValue.description).isEqualTo("some event contents")
    assertThat(captor.firstValue.additionalInformation.nomsNumber).isEqualTo("THIS_HAS_WORKED")
    assertThat(captor.firstValue.additionalInformation.reason).isEqualTo("RELEASED")
    assertThat(captor.firstValue.additionalInformation.details).isEqualTo("Movement reason code CR")
    assertThat(captor.firstValue.additionalInformation.currentLocation).isEqualTo("OUTSIDE_PRISON")
    assertThat(captor.firstValue.additionalInformation.prisonId).isEqualTo("MDI")
    assertThat(captor.firstValue.additionalInformation.currentPrisonStatus).isEqualTo("NOT_UNDER_PRISON_CARE")
  }

  private fun assertHasNoDlqMessages() {
    await untilCallTo { prisonerReleaseSqsClient.countMessagesOnQueue(prisonerReleaseDlqQueueUrl) } matches { it!! == 0 }
  }

  private fun assertPrisonerReleaseQueueHasProcessedMessages() {
    await untilCallTo { prisonerReleaseSqsClient.countMessagesOnQueue(prisonerReleaseQueueUrl) } matches { it == 0 }
  }

  private fun publishToTopic(event: PrisonerReleaseDateMessageEvent): PublishResult {

    val messageAttribute = MessageAttributeValue().withDataType("String").withStringValue(event.eventType)
    val eventJson = mapper.writeValueAsString(event)
    val publishRequest = PublishRequest(prisonerReleaseTopic.arn, eventJson)
      .withMessageAttributes(
        mapOf("eventType" to messageAttribute)
      )

    return prisonerReleaseTopic.snsClient.publish(publishRequest)
  }
}
