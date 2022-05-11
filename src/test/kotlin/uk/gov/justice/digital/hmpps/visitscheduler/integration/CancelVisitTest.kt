package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository

@DisplayName("Put /visits/{reference}/cancel")
class CancelVisitTest : IntegrationTestBase() {
  @Autowired
  private lateinit var visitRepository: VisitRepository

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `cancel visit by reference with outcome and outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.CANCELLED.name)
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.PRISONER_CANCELLED.name)
      .jsonPath("$.visitNotes.length()").isEqualTo(1)
      .jsonPath("$.visitNotes[0].type").isEqualTo("VISIT_OUTCOMES")
      .jsonPath("$.visitNotes[0].text").isEqualTo("Prisoner got covid")
  }

  @Test
  fun `cancel visit by reference with outcome and without outcome text`() {

    // Given
    val visit = createVisitAndSave()

    val outcomeDto = OutcomeDto(
      outcomeStatus = OutcomeStatus.VISITOR_CANCELLED
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(outcomeDto)
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.visitNotes").isEmpty
      .jsonPath("$.outcomeStatus").isEqualTo(OutcomeStatus.VISITOR_CANCELLED.name)
  }

  @Test
  fun `cancel visit by reference without outcome`() {

    // Given
    val visit = createVisitAndSave()

    // When
    val responseSpec = webTestClient.patch().uri("/visits/${visit.reference}/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `put visit by reference - not found`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ADMINISTRATIVE_ERROR,
      "Visit does not exist"
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/$reference/cancel")
      .headers(setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER")))
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.ESTABLISHMENT_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = webTestClient.patch().uri("/visits/$reference/cancel")
      .headers(setAuthorisation(roles = listOf()))
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val reference = "12345"

    val outcomeDto = OutcomeDto(
      OutcomeStatus.PRISONER_CANCELLED,
      "Prisoner got covid"
    )

    // When
    val responseSpec = webTestClient.put().uri("/visits/$reference/cancel")
      .body(
        BodyInserters.fromValue(
          outcomeDto
        )
      )
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun createVisitAndSave(): Visit {
    val visit = visitCreator(visitRepository)
      .withVisitStatus(BOOKED)
      .save()

    visitRepository.saveAndFlush(visit)
    return visit
  }
}
