package uk.gov.justice.digital.hmpps.visitscheduler.resource

import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.visitscheduler.config.TestClockConfiguration
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.sessionTemplateDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.SessionFrequency
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.SessionTemplateRepository
import java.time.LocalDate

@Import(TestClockConfiguration::class)
class VisitSessionsResourceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sessionTemplateRepository: SessionTemplateRepository

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @AfterEach
  internal fun deleteAllSessionTemplates() = sessionTemplateDeleter(sessionTemplateRepository)

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
    webTestClient.get().uri("/visit-sessions/prison/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(6)
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
          "2021-01-29T09:00:00",
          "2021-02-05T09:00:00",
          "2021-02-12T09:00:00" // 48 days maximum book ahead
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
    webTestClient.get().uri("/visit-sessions/prison/MDI")
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
    webTestClient.get().uri("/visit-sessions/prison/MDI")
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
    webTestClient.get().uri("/visit-sessions/prison/MDI")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(42) // up to 18/2
  }
}
