package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.controller.GET_VISIT_BY_REFERENCE
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitByReference
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
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
  fun `Booked visit by reference`() {

    // Given
    val createdVisit = createVisit(prisonerId = "FF0000AA", visitStatus = BOOKED, visitStart = visitTime)

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
    val createdVisit = createVisit(prisonerId = "FF0000AA", visitStatus = CANCELLED, visitStart = visitTime)

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
    val canceledVisit = createVisit(prisonerId = "FF0000AA", visitStatus = CANCELLED, visitStart = visitTime)
    val bookedVisit = createVisit(prisonerId = "FF0000AA", visitStatus = BOOKED, visitStart = visitTime, reference = canceledVisit.reference)

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

  private fun createVisit(
    visitStatus: VisitStatus = RESERVED,
    prisonerId: String = "FF0000AA",
    prisonId: String = "MDI",
    visitRoom: String = "A1",
    visitStart: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44),
    visitEnd: LocalDateTime = visitStart.plusHours(1),
    visitType: VisitType = VisitType.SOCIAL,
    visitRestriction: VisitRestriction = VisitRestriction.OPEN,
    reference: String = ""
  ): Visit {

    return visitRepository.saveAndFlush(
      Visit(
        visitStatus = visitStatus,
        prisonerId = prisonerId,
        prisonId = prisonId,
        visitRoom = visitRoom,
        visitStart = visitStart,
        visitEnd = visitEnd,
        visitType = visitType,
        visitRestriction = visitRestriction,
        _reference = reference
      )
    )
  }
}
