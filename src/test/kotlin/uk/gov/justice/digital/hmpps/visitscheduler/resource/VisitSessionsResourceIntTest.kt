package uk.gov.justice.digital.hmpps.visitscheduler.resource

import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import uk.gov.justice.digital.hmpps.visitscheduler.config.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import java.time.LocalDate

@Import(TestClockConfiguration::class)
class VisitSessionsResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateDeleter(sessionTemplateRepository)

  @AfterEach
  internal fun deleteAllVisitSessions() = visitDeleter(visitRepository)

  @Test
  fun `visit sessions are returned for a prison for a weekly schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2021-01-01"),
        frequency = SessionFrequency.WEEKLY,
        restrictions = "Only B wing"
      )
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
      .jsonPath("$[0].visitRoomName").isEqualTo("1")
      .jsonPath("$[0].prisonId").isEqualTo("MDI")
      .jsonPath("$[0].restrictions").isEqualTo("Only B wing")
      .jsonPath("$[0].openVisitCapacity").isEqualTo(10)
      .jsonPath("$[0].closedVisitCapacity").isEqualTo(5)
      .jsonPath("$[0].restrictions").isEqualTo("Only B wing")
      .jsonPath("$[0].endTimestamp").isEqualTo("2021-01-08T10:00:00")
      .jsonPath("$[0].visitType").isEqualTo("STANDARD_SOCIAL")
      .jsonPath("$[0].visitTypeDescription").isEqualTo("Standard Social")
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
  fun `expired visit sessions are not returned`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(
        startDate = LocalDate.parse("2020-01-01"),
        expiryDate = LocalDate.parse("2020-06-01"),
        frequency = SessionFrequency.WEEKLY,
        restrictions = "Only B wing"
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
        frequency = SessionFrequency.WEEKLY,
        restrictions = "Only B wing"
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
  fun `visit sessions are returned for a prison for a daily schedule`() {
    sessionTemplateCreator(
      repository = sessionTemplateRepository,
      sessionTemplate = sessionTemplate(startDate = LocalDate.parse("2021-01-08"))
    ).save()

    webTestClient.get().uri("/visit-sessions?prisonId=MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(22)
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
  fun `visit sessions are returned for a prisoner with a valid open non-association with a booking`() {
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
      startDate.minusMonths(6).toString()
    )

    webTestClient.get().uri("/visit-sessions?prisonId=$prisonId&prisonerId=$prisonerId")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(21)
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
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED_BY_PRISONER`() {
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
      .withStatus(VisitStatus.CANCELLED_BY_PRISONER)
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
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED_BY_VISITOR`() {
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
      .withStatus(VisitStatus.CANCELLED_BY_VISITOR)
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
  fun `visit sessions are returned for a prisoner with a valid non-association with a booking CANCELLED_BY_PRISON`() {
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
      .withStatus(VisitStatus.CANCELLED_BY_PRISON)
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
