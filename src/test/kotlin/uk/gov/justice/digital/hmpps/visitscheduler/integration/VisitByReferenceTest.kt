package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  @Test
  fun `Visit by reference`() {

    // Given
    val createdVisit = visitCreator(visitRepository)
      .withPrisonerId("FF0000AA")
      .withVisitStatus(BOOKED)
      .withVisitStart(visitTime)
      .save()

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
  }

  @Test
  fun `Visit by reference - not found`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isNotFound
  }
}
