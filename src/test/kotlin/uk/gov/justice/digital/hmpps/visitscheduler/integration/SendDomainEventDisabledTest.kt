package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callBookVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@TestPropertySource(properties = ["feature.events.sns.enabled=false"])
class SendDomainEventDisabledTest : IntegrationTestBase() {

  companion object {
    val ROLES: List<String> = listOf("ROLE_VISIT_SCHEDULER")
  }

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  internal val testQueue by lazy {
    hmppsQueueService.findByQueueId("domaineventsqueue")
      ?: throw RuntimeException("Queue with name domaineventstestqueue doesn't exist")
  }
  internal val testSqsClient by lazy { testQueue.sqsClient }
  internal val testQueueUrl by lazy { testQueue.queueUrl }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @BeforeEach
  fun `clear queues`() {
    testSqsClient.purgeQueue(PurgeQueueRequest(testQueueUrl))
  }

  @Test
  fun `booked visit no event sent`() {

    // Given
    val visitEntity = createVisitAndSave(VisitStatus.RESERVED)
    val reference = visitEntity.reference
    val authHeader = setAuthorisation(roles = ROLES)

    // When
    val responseSpec = callBookVisit(webTestClient, authHeader, reference)

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
    val visitEntity = createVisitAndSave(VisitStatus.BOOKED)
    val reference = visitEntity.reference
    val authHeader = setAuthorisation(roles = ROLES)
    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    callCancelVisit(webTestClient, authHeader, reference, outcomeDto)

    // Then
    assertSNSEventsNotSent()
  }

  private fun createVisitAndSave(visitStatus: VisitStatus): Visit {
    val visit = visitCreator(visitRepository)
      .withVisitStatus(visitStatus)
      .save()
    visitRepository.saveAndFlush(visit)
    return visit
  }

  private fun assertSNSEventsNotSent() {
    await untilCallTo { testQueueEventMessageCount() } matches { it == 0 }
  }

  private fun testQueueEventMessageCount(): Int? {
    val queueAttributes = testSqsClient.getQueueAttributes(testQueueUrl, listOf("ApproximateNumberOfMessages"))
    return queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  }
}
