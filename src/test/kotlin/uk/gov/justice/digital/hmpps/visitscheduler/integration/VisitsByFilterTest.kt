package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingNote
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingSupport
import uk.gov.justice.digital.hmpps.visitscheduler.helper.createBookingVisitor
import uk.gov.justice.digital.hmpps.visitscheduler.helper.defaultBooking
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationCreator
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Reservation
import uk.gov.justice.digital.hmpps.visitscheduler.repository.BookingRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import java.time.LocalDateTime

@DisplayName("GET /visits")
class VisitsByFilterTest : IntegrationTestBase() {

  private val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)

  @Autowired
  private lateinit var reservationRepository: ReservationRepository

  @Autowired
  private lateinit var bookingRepository: BookingRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @AfterEach
  internal fun deleteAllReservations() = reservationDeleter(reservationRepository)

  private lateinit var reservationMin: Reservation
  private lateinit var reservationFull: Reservation

  @BeforeEach
  internal fun createReservations() {

    reservationMin = reservationCreator(reservationRepository)
      .withVisitStart(visitTime)
      .save()
    val bookingMin = defaultBooking(reservationMin)
    bookingMin.prisonId = "MDI"
    bookingMin.prisonerId = "FF0000AA"
    val bookingMinEntity = bookingRepository.save(bookingMin)
    reservationMin.booking = bookingMinEntity
    reservationRepository.saveAndFlush(reservationMin)

    reservationFull = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusDays(1))
      .withVisitEnd(visitTime.plusDays(1).plusHours(1))
      .save()
    val bookingFull = defaultBooking(reservationFull)
    bookingFull.prisonId = "LEI"
    bookingFull.prisonerId = "FF0000BB"
    val bookingFullEntity = bookingRepository.save(bookingFull)
    reservationFull.booking = bookingFullEntity
    reservationFull.booking!!.visitNotes.add(
      createBookingNote(reservationFull.booking!!, text = "A visit concern", type = NoteType.VISITOR_CONCERN)
    )
    reservationFull.booking!!.visitNotes.add(
      createBookingNote(reservationFull.booking!!, text = "A visit outcome", type = NoteType.VISIT_OUTCOMES)
    )
    reservationFull.booking!!.visitNotes.add(
      createBookingNote(reservationFull.booking!!, text = "A visit comment", type = NoteType.VISIT_COMMENT)
    )
    reservationFull.booking!!.visitNotes.add(
      createBookingNote(reservationFull.booking!!, text = "Status has changed", type = NoteType.STATUS_CHANGED_REASON)
    )
    reservationRepository.saveAndFlush(reservationFull)

    val visitCC = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusDays(2))
      .withVisitEnd(visitTime.plusDays(2).plusHours(1))
      .save()
    val bookingCC = defaultBooking(visitCC)
    bookingCC.prisonId = "LEI"
    bookingCC.prisonerId = "FF0000CC"
    val bookingCCEntity = bookingRepository.save(bookingCC)
    visitCC.booking = bookingCCEntity
    visitCC.booking!!.visitContact =
      createBookingContact(visitCC.booking!!, name = "Jane Doe", telephone = "01234 098765")
    visitCC.booking!!.visitors.add(
      createBookingVisitor(visitCC.booking!!, personId = 123L)
    )
    visitCC.booking!!.support.add(
      createBookingSupport(visitCC.booking!!, type = "OTHER", text = "Some Text")
    )
    reservationRepository.saveAndFlush(visitCC)

    val visitRESERVED = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusHours(1))
      .withVisitEnd(visitTime.plusHours(2))
      .save()
    val bookingRESERVED = defaultBooking(visitRESERVED)
    bookingRESERVED.prisonId = "BEI"
    bookingRESERVED.prisonerId = "GG0000BB"
    bookingRESERVED.visitStatus = StatusType.RESERVED
    val bookingRESERVEDEntity = bookingRepository.save(bookingRESERVED)
    visitRESERVED.booking = bookingRESERVEDEntity
    reservationRepository.saveAndFlush(visitRESERVED)

    val visitBOOKED = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusDays(1).plusHours(1))
      .withVisitEnd(visitTime.plusDays(1).plusHours(2))
      .save()
    val bookingBOOKED = defaultBooking(visitBOOKED)
    bookingBOOKED.prisonId = "BEI"
    bookingBOOKED.prisonerId = "GG0000BB"
    bookingBOOKED.visitStatus = StatusType.BOOKED
    val bookingBOOKEDEntity = bookingRepository.save(bookingBOOKED)
    visitBOOKED.booking = bookingBOOKEDEntity
    reservationRepository.saveAndFlush(visitBOOKED)

    val visitCANCELLED = reservationCreator(reservationRepository)
      .withVisitStart(visitTime.plusDays(2).plusHours(1))
      .withVisitEnd(visitTime.plusDays(2).plusHours(2))
      .save()
    val bookingCANCELLED = defaultBooking(visitCANCELLED)
    bookingCANCELLED.prisonId = "BEI"
    bookingCANCELLED.prisonerId = "GG0000BB"
    bookingCANCELLED.visitStatus = StatusType.CANCELLED
    val bookingCANCELLEDEntity = bookingRepository.save(bookingCANCELLED)
    visitCANCELLED.booking = bookingCANCELLEDEntity
    reservationRepository.saveAndFlush(visitCANCELLED)
  }

  @Test
  fun `get visit by prisoner ID`() {

    // Given
    val prisonerId = "FF0000BB"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId")

    // Then
    responseSpec
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000BB")
      .jsonPath("$[0].startTimestamp").isEqualTo(reservationFull.visitStart.toString())
      .jsonPath("$[0].reference").exists()
      .jsonPath("$[0].visitNotes[?(@.type=='VISITOR_CONCERN')].text").isEqualTo("A visit concern")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_COMMENT')].text").isEqualTo("A visit comment")
      .jsonPath("$[0].visitNotes[?(@.type=='VISIT_OUTCOMES')].text").isEqualTo("A visit outcome")
      .jsonPath("$[0].visitNotes[?(@.type=='STATUS_CHANGED_REASON')].text").isEqualTo("Status has changed")
  }

  @Test
  fun `get visit by prison ID`() {
    // Given
    val prisonId = "LEI"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonId=$prisonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "FF0000CC",
          "FF0000BB",
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

    // Given
    val prisonId = "LEI"
    val startTimestamp = "2021-11-03T09:00:00"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonId=$prisonId&startTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].startTimestamp").isEqualTo(visitTime.plusDays(2).toString())
  }

  @Test
  fun `get visits by prisoner ID, prison ID and starting on or after a specified date and time`() {

    // Given
    val prisonId = "BEI"
    val startTimestamp = "2021-11-01T13:30:45"
    val prisonerId = "GG0000BB"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId&prisonId=$prisonId&startTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-02T13:30:44",
        )
      )
      .jsonPath("$..prisonId").value(
        Matchers.contains(
          "BEI",
          "BEI"
        )
      )
      .jsonPath("$..prisonerId").value(
        Matchers.contains(
          "GG0000BB",
          "GG0000BB"
        )
      )
  }

  @Test
  fun `get visits starting before a specified date`() {
    // Given
    val startTimestamp = "2021-11-03T09:00:00"

    // When
    val responseSpec = callVisitEndPoint("/visits?endTimestamp=$startTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
          "2021-11-01T13:30:44",
          "2021-11-01T12:30:44"
        )
      )
  }

  @Test
  fun `get visits starting within a date range`() {
    // Given
    val startTimestamp = "2021-11-02T09:00:00"
    val endTimestamp = "2021-11-03T09:00:00"

    // When
    val responseSpec = callVisitEndPoint("/visits?startTimestamp=$startTimestamp&endTimestamp=$endTimestamp")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$..startTimestamp").value(
        Matchers.contains(
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
        )
      )
  }

  @Test
  fun `get visits by visitor`() {

    // Given
    val nomisPersonId = 123

    // When
    val responseSpec = callVisitEndPoint("/visits?nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("FF0000CC")
  }

  @Test
  fun `get visits by status`() {
    // Given
    val visitStatus = StatusType.BOOKED

    // When
    val responseSpec = callVisitEndPoint("/visits?visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerId").isEqualTo("GG0000BB")
  }

  @Test
  fun `get visits paged`() {

    // Given
    val size = 4

    // When
    val responseSpecFirst = callVisitEndPoint("/visits?page=0&size=$size")
    val responseSpecLast = callVisitEndPoint("/visits?page=1&size=$size")

    // Then - Page 0
    responseSpecFirst.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(4)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-03T13:30:44",
          "2021-11-03T12:30:44",
          "2021-11-02T13:30:44",
          "2021-11-02T12:30:44",
        )
      )

    // And - Page 1
    responseSpecLast.expectStatus().isOk
      .expectBody()
      .jsonPath("$.content.length()").isEqualTo(2)
      .jsonPath("$.content..startTimestamp").value(
        Matchers.contains(
          "2021-11-01T13:30:44",
          "2021-11-01T12:30:44",
        )
      )
  }

  @Test
  fun `no visits found for prisoner`() {
    // Given
    val prisonerId = 12345

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId")

    // Then
    responseSpec.expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(0)
  }

  @Test
  fun `get visits - invalid request, contact id should be a long`() {

    // Given
    val nomisPersonId = "123LL"

    // When
    val responseSpec = callVisitEndPoint("/visits?nomisPersonId=$nomisPersonId")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `get visits by invalid status`() {
    // Given
    val visitStatus = "AnythingWillDo"

    // When

    val responseSpec = callVisitEndPoint("/visits?visitStatus=$visitStatus")

    // Then
    responseSpec.expectStatus().isBadRequest
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {
    // Given
    val prisonerId = "FF0000AA"

    // When
    val responseSpec = callVisitEndPoint("/visits?prisonerId=$prisonerId", listOf())

    // Then
    responseSpec.expectStatus().isForbidden
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val prisonerId = "FF0000AA"

    // When
    val responseSpec = webTestClient.get().uri("/visits?prisonerId=$prisonerId").exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  fun callVisitEndPoint(url: String, roles: List<String> = listOf("ROLE_VISIT_SCHEDULER")): ResponseSpec {
    return webTestClient.get().uri(url)
      .headers(setAuthorisation(roles = roles))
      .exchange()
  }
}
