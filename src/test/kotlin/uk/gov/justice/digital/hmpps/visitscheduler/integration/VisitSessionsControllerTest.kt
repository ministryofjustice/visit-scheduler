package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.visitscheduler.helper.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.DAILY
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.MONTHLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.SINGLE
import uk.gov.justice.digital.hmpps.visitscheduler.model.SessionFrequency.WEEKLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate

@Import(TestClockConfiguration::class)
class VisitSessionsControllerTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateDeleter(sessionTemplateRepository)

  @AfterEach
  internal fun deleteAllVisitSessions() = visitDeleter(visitRepository)

  @Test
  fun `visit sessions are returned for a prison for a single schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2021-01-08"),
        frequency = SINGLE
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonId").isEqualTo("MDI")
      .jsonPath("$[0].visitRoomName").isEqualTo("1")
      .jsonPath("$[0].visitType").isEqualTo(VisitType.SOCIAL.name)
      .jsonPath("$[0].openVisitCapacity").isEqualTo(10)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(0)
      .jsonPath("$[0].closedVisitCapacity").isEqualTo(5)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
      .jsonPath("$[0].startTimestamp").isEqualTo("2021-01-08T09:00:00")
      .jsonPath("$[0].endTimestamp").isEqualTo("2021-01-08T10:00:00")
  }

  @Test
  fun `visit sessions are returned for a prison for a daily schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2021-01-08"),
        expiryDate = LocalDate.parse("2021-01-14"),
        frequency = DAILY,
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(7)
  }

  @Test
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2021-01-08"),
        frequency = WEEKLY
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
      .jsonPath("$[0].endTimestamp").isEqualTo("2021-01-08T10:00:00")
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-01-08T09:00:00",
          "2021-01-15T09:00:00",
          "2021-01-22T09:00:00",
          "2021-01-29T09:00:00"
        )
      )
  }

  @Test
  fun `visit sessions are returned for a prison for a monthly schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2021-01-08"),
        frequency = MONTHLY
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo("2021-01-08T09:00:00")
      .jsonPath("$[0].endTimestamp").isEqualTo("2021-01-08T10:00:00")
  }

  @Test
  fun `expired visit sessions are not returned`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2020-01-01"),
        expiryDate = LocalDate.parse("2020-06-01"),
        frequency = WEEKLY
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `sessions that start after the book ahead period are not returned`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2022-01-01"),
        expiryDate = LocalDate.parse("2022-06-01"),
        frequency = WEEKLY
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `visit sessions include reserved and booked open visit count`() {
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val session = sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = dateTime.toLocalDate(),
        startTime = dateTime.toLocalTime(),
        endTime = dateTime.plusHours(1).toLocalTime(),
        frequency = SINGLE
      )
    ).save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.RESERVED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.CANCELLED)
      .save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(2)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions include visit count one matching room`() {
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val session = sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = dateTime.toLocalDate(),
        startTime = dateTime.toLocalTime(),
        endTime = dateTime.plusHours(1).toLocalTime(),
        frequency = SINGLE
      )
    ).save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom + "Anythingwilldo")
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(1)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }
  @Test
  fun `visit sessions include reserved and booked closed visit count`() {
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)
    val session = sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = dateTime.toLocalDate(),
        startTime = dateTime.toLocalTime(),
        endTime = dateTime.plusHours(1).toLocalTime(),
        frequency = SINGLE
      )
    ).save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.RESERVED)
      .withRestriction(VisitRestriction.CLOSED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.BOOKED)
      .withRestriction(VisitRestriction.CLOSED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.CANCELLED)
      .withRestriction(VisitRestriction.CLOSED)
      .save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(0)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(2)
  }

  @Test
  fun `visit sessions visit count includes only visits within session period`() {
    val dateTime = LocalDate.parse("2021-01-08").atTime(9, 0)

    val session = sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = dateTime.toLocalDate(),
        startTime = dateTime.toLocalTime(),
        endTime = dateTime.plusHours(1).toLocalTime(),
        frequency = SINGLE
      )
    ).save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime.minusHours(1))
      .withVisitEnd(dateTime)
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime)
      .withVisitEnd(dateTime.plusMinutes(30))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime.plusMinutes(30))
      .withVisitEnd(dateTime.plusHours(1))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()
    visitCreator(visitRepository)
      .withVisitRoom(session.visitRoom)
      .withVisitStart(dateTime.plusHours(1))
      .withVisitEnd(dateTime.plusHours(2))
      .withVisitStatus(VisitStatus.BOOKED)
      .save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].openVisitBookedCount").isEqualTo(2)
      .jsonPath("$[0].closedVisitBookedCount").isEqualTo(0)
  }

  @Test
  fun `visit sessions are returned for a prisoner without any non-associations`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()

    prisonApiMockServer.stubGetOffenderNonAssociationEmpty(prisonerId)

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association without a booking`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a future non-association with a booking`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.atTime(9, 0))
      .withVisitEnd(startDate.atTime(9, 30))
      .withPrisonId(prisonId)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.plusMonths(6).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with an expired non-association with a booking`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.atTime(9, 0))
      .withVisitEnd(startDate.atTime(9, 30))
      .withPrisonId(prisonId)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusMonths(6).toString(),
      startDate.minusMonths(1).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.atTime(9, 0))
      .withVisitEnd(startDate.atTime(9, 30))
      .withPrisonId(prisonId)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(21)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.atTime(9, 0))
      .withVisitEnd(startDate.atTime(9, 30))
      .withPrisonId(prisonId)
      .withVisitStatus(VisitStatus.CANCELLED)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the past`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.minusMonths(6).atTime(9, 0))
      .withVisitEnd(startDate.minusMonths(6).atTime(9, 30))
      .withPrisonId(prisonId)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusMonths(6).toString(),
      startDate.plusMonths(1).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }

  @Test
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking in the future`() {
    val prisonId = "MDI"
    val prisonerId = "A1234AA"
    val associationId = "B1234BB"
    val startDate = LocalDate.parse("2021-01-08")
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = startDate)
    ).save()
    visitCreator(visitRepository)
      .withPrisonerId(associationId)
      .withVisitStart(startDate.plusMonths(6).atTime(9, 0))
      .withVisitEnd(startDate.plusMonths(6).atTime(9, 30))
      .withPrisonId(prisonId)
      .save()

    prisonApiMockServer.stubGetOffenderNonAssociation(
      prisonerId,
      associationId,
      startDate.minusYears(1).toString(),
      startDate.plusYears(1).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
  }
}
