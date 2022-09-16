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
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ReserveVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateReservationRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime

@TestPropertySource(properties = ["feature.events.sns.enabled=false"])
class SendDomainEventDisabledTest : IntegrationTestBase() {

  companion object {
    val ROLES: List<String> = listOf("ROLE_VISIT_SCHEDULER")
  }

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

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
  fun `send visit booked event on create`() {

    // Given
    val requestDto = createVisitRequest()

    // When
    webTestClient.post().uri("/visits")
      .headers(setAuthorisation(roles = ROLES))
      .body(
        BodyInserters.fromValue(requestDto)
      )
      .exchange()

    // Then
    assertSNSEventsNotSent()
  }

  @Test
  fun `send visit booked event on update`() {

    // Given
    val visitEntity = createVisitAndSave(VisitStatus.RESERVED)

    // When
    webTestClient.put().uri("/visits/${visitEntity.reference}")
      .headers(setAuthorisation(roles = ROLES))
      .body(
        BodyInserters.fromValue(UpdateReservationRequestDto())
      )
      .exchange()

    // Then
    assertSNSEventsNotSent()
  }

  @Test
  fun `send visit cancelled event`() {
    // Given
    val visitEntity = createVisitAndSave(VisitStatus.BOOKED)

    // When
    webTestClient.patch().uri("/visits/${visitEntity.reference}/cancel")
      .headers(setAuthorisation(roles = ROLES))
      .body(
        BodyInserters.fromValue(OutcomeDto(OutcomeStatus.PRISONER_CANCELLED, "AnyThingWillDo"))
      )
      .exchange()

    // Then
    assertSNSEventsNotSent()
  }

  private fun createVisitRequest(): ReserveVisitRequestDto {
    return ReserveVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitRestriction = VisitRestriction.OPEN,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123, visitContact = true)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text"))
    )
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
