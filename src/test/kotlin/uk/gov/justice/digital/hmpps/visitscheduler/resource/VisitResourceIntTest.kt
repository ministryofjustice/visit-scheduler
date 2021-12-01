package uk.gov.justice.digital.hmpps.visitscheduler.resource

import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.data.CreateVisitRequest
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitVisitorCreator
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.jpa.repository.VisitVisitorRepository
import java.time.LocalDateTime

class VisitResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var visitVisitorRepository: VisitVisitorRepository

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @DisplayName("GET /visits")
  @Nested
  inner class GetVisitsByFilter {
    @BeforeEach
    internal fun createVisits() {
      visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .save()
      visitCreator(visitRepository)
        .withPrisonerId("FF0000BB")
        .withVisitStart(visitTime.plusDays(1))
        .withVisitEnd(visitTime.plusDays(1).plusHours(1))
        .withPrisonId("LEI")
        .save()
      val visitCC = visitCreator(visitRepository)
        .withPrisonerId("FF0000CC")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withPrisonId("LEI")
        .save()
      visitVisitorCreator(repository = visitVisitorRepository, contactId = 123L, visitId = visitCC.id, visitCC)
    }

    @Test
    fun `get visit by prisoner ID`() {

      webTestClient.get().uri("/visits?prisonerId=FF0000AA")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[-1].prisonerId").isEqualTo("FF0000AA")
        .jsonPath("$[-1].startTimestamp").isEqualTo(visitTime.toString())
        .jsonPath("$[-1].id").exists()
    }

    @Test
    fun `get visit by prison ID`() {

      webTestClient.get().uri("/visits?prisonId=LEI")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$..prisonerId").value(
          Matchers.contains(
            "FF0000BB",
            "FF0000CC"
          )
        )
        .jsonPath("$..prisonId").value(
          Matchers.contains(
            "LEI",
            "LEI"
          )
        )
    }

    @Test
    fun `get visits by prison ID and starting on or after a specified date`() {

      webTestClient.get().uri("/visits?prisonId=LEI&startTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
    }

    @Test
    fun `get visits starting before a specified date`() {

      webTestClient.get().uri("/visits?endTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$..startTimestamp").value(
          Matchers.contains(
            "2021-11-01T12:30:44",
            "2021-11-02T12:30:44"
          )
        )
    }

    @Test
    fun `get visits by visitor`() {

      webTestClient.get().uri("/visits?contactId=123")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
    }

    @Test
    fun `get visits starting within a date range`() {

      webTestClient.get().uri("/visits?startTimestamp=2021-11-02T09:00:00&endTimestamp=2021-11-03T09:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$..startTimestamp").value(
          Matchers.contains(
            "2021-11-02T12:30:44"
          )
        )
    }

    @Test
    fun `get visit by visit id`() {

      val createdVisit = visitCreator(visitRepository)
        .withPrisonerId("FF0000AA")
        .withVisitStart(visitTime)
        .save()

      webTestClient.get().uri("/visits/${createdVisit.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(createdVisit.id)
    }

    @Test
    fun `get visit by visit id - not found`() {
      webTestClient.get().uri("/visits/12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `access forbidden when no role`() {

      webTestClient.get().uri("/visits?prisonerId=FF0000AA")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `unauthorised when no token`() {

      webTestClient.get().uri("/visits?prisonerId=FF0000AA")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `no visits found for prisoner`() {

      webTestClient.get().uri("/visits?prisonerId=12345")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0)
    }
  }

  @DisplayName("DELETE /visits/{visitId}")
  @Nested
  inner class DeleteVisitById {
    @Test
    fun `delete visit by visit id`() {

      val visitCC = visitCreator(visitRepository)
        .withPrisonerId("FF0000CC")
        .withVisitStart(visitTime.plusDays(2))
        .withVisitEnd(visitTime.plusDays(2).plusHours(1))
        .withPrisonId("LEI")
        .save()
      visitVisitorCreator(repository = visitVisitorRepository, contactId = 123L, visitId = visitCC.id, visitCC)

      webTestClient.delete().uri("/visits/${visitCC.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk

      webTestClient.get().uri("/visits/${visitCC.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @DisplayName("CREATE /visits/{visitId}")
  @Nested
  inner class CreateVisit {
    val createVisitRequest = CreateVisitRequest(
      prisonerId = "FF0000FF",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitRoom = "A1",
      visitType = VisitType.STANDARD_SOCIAL,
      prisonId = "MDI",
      contactIdList = listOf(123)
    )

    @Test
    fun `create visit`() {
      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .body(
          BodyInserters.fromValue(
            createVisitRequest
          )
        )
        .exchange()
        .expectStatus().isCreated

      webTestClient.get().uri("/visits?prisonerId=FF0000FF")
        .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].prisonerId").isEqualTo("FF0000FF")
        .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.toString())
        .jsonPath("$[0].endTimestamp").isEqualTo(visitTime.plusHours(1).toString())
        .jsonPath("$[0].visitRoom").isEqualTo("A1")
        .jsonPath("$[0].prisonId").isEqualTo("MDI")
        .jsonPath("$[0].visitType").isEqualTo("STANDARD_SOCIAL")
        .jsonPath("$[0].visitTypeDescription").isEqualTo("Standard Social")
        .jsonPath("$[0].statusDescription").isEqualTo("Reserved")
        .jsonPath("$[0].status").isEqualTo("RESERVED")
        .jsonPath("$[0].id").isNumber
        .jsonPath("$[0].visitors.length()").isEqualTo(1)
        .jsonPath("$[0].visitors[0].contactId").isEqualTo(123)
        .jsonPath("$[0].visitors[0].visitId").isNumber
    }

    @Test
    fun `access forbidden when no role`() {

      webTestClient.post().uri("/visits")
        .headers(setAuthorisation(roles = listOf()))
        .body(
          BodyInserters.fromValue(
            createVisitRequest
          )
        )
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `unauthorised when no token`() {

      webTestClient.post().uri("/visits")
        .body(
          BodyInserters.fromValue(
            createVisitRequest
          )
        )
        .exchange()
        .expectStatus().isUnauthorized
    }
  }
}
