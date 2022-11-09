package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `Booked visit by reference`() {

    // Given
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, visitStart = visitTime)

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
  }

  @Test
  fun `Canceled visit by reference`() {

    // Given
    val createdVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = CANCELLED, visitStart = visitTime)

    val reference = createdVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
  }

  @Test
  fun `Gets latest visit`() {

    // Given
    val canceledVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = CANCELLED, visitStart = visitTime)
    val bookedVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, visitStart = visitTime, reference = canceledVisit.reference)

    val reference = bookedVisit.reference

    // When
    val responseSpec = callVisitByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(reference)
      .jsonPath("$.visitStatus").isEqualTo("BOOKED")
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
