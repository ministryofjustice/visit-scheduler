package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.helper.defaultBooking
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import java.time.LocalDateTime

@DisplayName("GET /visits/{reference}")
class VisitsByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var reservationRepository: ReservationRepository

  @AfterEach
  internal fun deleteAllVisits() = reservationDeleter(reservationRepository)

  @BeforeEach
  internal fun createVisits() {
    val reservation = reservationCreator(reservationRepository)
      .withVisitStart(visitTime)
      .save()

    val booking = defaultBooking(reservation)
    booking.prisonerId = "FF0000AA"
    booking.prisonId = "MDI"
    reservation.booking = booking

    reservationRepository.saveAndFlush(reservation)
  }

  @Test
  fun `get visit by reference`() {

    // Given
    val reservation = reservationCreator(reservationRepository)
      .withVisitStart(visitTime)
      .save()

    val booking = defaultBooking(reservation)
    booking.prisonerId = "FF0000AA"
    reservation.booking = booking

    val createdVisit = reservationRepository.saveAndFlush(reservation)

    // When
    val responseSpec = callVisitEndPoint("/visits/${createdVisit.reference}")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.reference").isEqualTo(createdVisit.reference)
  }

  @Test
  fun `get visit by reference - not found`() {
    // Given
    val reference = "12345"

    // When
    val responseSpec = callVisitEndPoint("/visits/$reference")

    // Then
    responseSpec.expectStatus().isNotFound
  }

  fun callVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
