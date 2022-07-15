package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingContact
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingSupport
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.helper.defaultBooking
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.repository.BookingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import java.time.LocalDateTime

@DisplayName("DELETE /visits/{reference}")
class DeleteVisitByReferenceTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var reservationRepository: ReservationRepository

  @Autowired
  private lateinit var bookingRepository: BookingRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllVisits() = reservationDeleter(reservationRepository)

  @Test
  fun `delete visit by reference`() {

    // Given
    val reservation = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusDays(2))
      .withVisitEnd(visitTime.plusDays(2).plusHours(1))
      .save()
    val booking = defaultBooking(reservation)
    booking.prisonerId = "FF0000CC"
    booking.prisonId = "LEI"
    val bookingEntity = bookingRepository.save(booking)
    reservation.booking = bookingEntity
    reservation.booking!!.visitContact = createBookingContact(reservation.booking!!, name = "Jane Doe", telephone = "01234 098765")
    reservation.booking!!.visitors.add(
      createBookingVisitor(reservation.booking!!, personId = 123L)
    )
    reservation.booking!!.support.add(
      createBookingSupport(reservation.booking!!, type = "OTHER", text = "Some Text")
    )
    reservationRepository.saveAndFlush(reservation)

    // When
    val responseSpec = callDeleteVisitEndPoint("/visits/${reservation.reference}")

    // Then
    responseSpec.expectStatus().isOk

    val visit = reservationRepository.findByReference(reservation.reference)
    Assertions.assertThat(visit).isNull()

    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-deleted"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(reservation.reference)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-deleted"), any(), isNull())
  }

  @Test
  fun `delete visit by reference NOT Found`() {
    // Given
    val reference = "123456"

    // When
    val responseSpec = callDeleteVisitEndPoint("/visits/$reference")

    // Then
    responseSpec.expectStatus().isOk

    verify(telemetryClient, times(0)).trackEvent(eq("visit-scheduler-prison-visit-deleted"), any(), isNull())
  }

  fun callDeleteVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.delete().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
