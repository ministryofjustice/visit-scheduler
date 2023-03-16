package uk.gov.justice.digital.hmpps.visitscheduler.integration.events

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@TestPropertySource(properties = ["feature.events.sns.enabled=false"])
class SendDomainEventDisabledTest : IntegrationTestBase() {

  companion object {
    val ROLES: List<String> = listOf("ROLE_VISIT_SCHEDULER")
  }

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  internal val testQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue")
      ?: throw RuntimeException("Queue with name domaineventstestqueue doesn't exist")
  }
  internal val testSqsClient by lazy { testQueue.sqsClient }
  internal val testQueueUrl by lazy { testQueue.queueUrl }

  @BeforeEach
  fun `clear queues`() {
    testSqsClient.purgeQueue(PurgeQueueRequest(testQueueUrl))
  }

  @Test
  fun `booked visit no event sent`() {

    // Given
    val visitEntity = visitEntityHelper.create(visitStatus = VisitStatus.RESERVED)
    val applicationReference = visitEntity.applicationReference
    val authHeader = setAuthorisation(roles = ROLES)

    // When
    val responseSpec = callVisitBook(webTestClient, authHeader, applicationReference)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    assertSNSEventsNotSent()
  }

  @Test
  fun `cancelled visit no event sent`() {
    // Given
    val visitEntity = visitEntityHelper.create(visitStatus = VisitStatus.BOOKED)
    val reference = visitEntity.reference
    val authHeader = setAuthorisation(roles = ROLES)
    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        OutcomeStatus.PRISONER_CANCELLED,
        "Prisoner got covid"
      ),
      "user-1",
    )

    // When
    callCancelVisit(webTestClient, authHeader, reference, cancelVisitDto)

    // Then
    assertSNSEventsNotSent()
  }

  private fun assertSNSEventsNotSent() {
    await untilCallTo { testQueueEventMessageCount() } matches { it == 0 }
  }

  private fun testQueueEventMessageCount(): Int? {
    val queueAttributes = testSqsClient.getQueueAttributes(testQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }
}
