package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitHistoryByReference
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CHANGING
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import java.time.LocalDate
import java.time.LocalDateTime

@DisplayName("GET $GET_VISIT_BY_REFERENCE")
class VisitHistoryByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `visit history by reference`() {
    // Given
    val bookedVisit = visitEntityHelper.create(prisonerId = "FF0000AA", visitStatus = BOOKED, visitStart = visitTime)
    val reference = bookedVisit.reference
    visitEntityHelper.create(reference = reference, prisonerId = "FF0000AA", visitStatus = CANCELLED, visitStart = visitTime)
    visitEntityHelper.create(reference = reference, prisonerId = "FF0000AA", visitStatus = RESERVED, visitStart = visitTime)
    visitEntityHelper.create(reference = reference, prisonerId = "FF0000AA", visitStatus = CHANGING, visitStart = visitTime)

    // When
    val responseSpec = callVisitHistoryByReference(webTestClient, reference, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$[0].reference").isEqualTo(reference)
      .jsonPath("$[0].visitStatus").isEqualTo(BOOKED.name)
      .jsonPath("$[1].reference").isEqualTo(reference)
      .jsonPath("$[1].visitStatus").isEqualTo(CANCELLED.name)

  }
}
