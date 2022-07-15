package uk.gov.justice.digital.hmpps.visitscheduler.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.migration.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.migration.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.migration.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.NoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.reservation.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.reservationDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.NoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.NOT_RECORDED
import uk.gov.justice.digital.hmpps.visitscheduler.model.RestrictionType.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.StatusType.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Note
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.ReservationRepository
import java.time.LocalDateTime

private const val TEST_END_POINT = "/migrate-visits"

// @Transactional(propagation = SUPPORTS)
@Transactional
@DisplayName("Migrate POST /visits")
class MigrateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var reservationRepository: ReservationRepository

  @Autowired
  private lateinit var legacyDataRepository: LegacyDataRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @AfterEach
  internal fun deleteAllVisits() = reservationDeleter(reservationRepository)

  private fun createMigrateVisitRequestDto(): MigrateVisitRequestDto {
    return MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      outcomeStatus = COMPLETED_NORMALLY,
      visitRestriction = OPEN,
      visitContact = CreateLegacyContactOnVisitRequestDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123)),
      visitNotes = setOf(
        NoteDto(type = VISITOR_CONCERN, "A visit concern"),
        NoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        NoteDto(type = VISIT_COMMENT, "A visit comment"),
        NoteDto(type = STATUS_CHANGED_REASON, "Status has changed")
      ),
      legacyData = CreateLegacyDataRequestDto(123)
    )
  }

  @Test
  fun `migrate visit`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createMigrateVisitRequestDto()
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val reservation = reservationRepository.findByReference(reference)
    assertThat(reservation).isNotNull
    assertThat(reservation!!.reference).isEqualTo(reference)
    assertThat(reservation.visitRoom).isEqualTo("A1")
    assertThat(reservation.visitStart).isEqualTo(visitTime.toString())
    assertThat(reservation.visitEnd).isEqualTo(visitTime.plusHours(1).toString())
    assertThat(reservation.visitRestriction).isEqualTo(OPEN)
    assertThat(reservation.createTimestamp).isNotNull

    assertThat(reservation.booking).isNotNull
    assertThat(reservation.booking!!.prisonId).isEqualTo("MDI")
    assertThat(reservation.booking!!.prisonerId).isEqualTo("FF0000FF")
    assertThat(reservation.booking!!.visitType).isEqualTo(SOCIAL)
    assertThat(reservation.booking!!.visitStatus).isEqualTo(RESERVED)
    assertThat(reservation.booking!!.outcomeStatus).isEqualTo(COMPLETED_NORMALLY)
    assertThat(reservation.booking!!.visitContact!!.name).isEqualTo("John Smith")
    assertThat(reservation.booking!!.visitContact!!.telephone).isEqualTo("013448811538")
    assertThat(reservation.booking!!.visitors.size).isEqualTo(1)
    assertThat(reservation.booking!!.visitors[0].nomisPersonId).isEqualTo(123)
    assertThat(reservation.booking!!.visitNotes)
      .hasSize(4)
      .extracting(Note::type, Note::text)
      .containsExactlyInAnyOrder(
        tuple(VISITOR_CONCERN, "A visit concern"),
        tuple(VISIT_OUTCOMES, "A visit outcome"),
        tuple(VISIT_COMMENT, "A visit comment"),
        tuple(STATUS_CHANGED_REASON, "Status has changed")
      )

    val legacyData = legacyDataRepository.findByBookingId(reservation.booking!!.id)
    assertThat(legacyData).isNotNull
    assertThat(legacyData!!.bookingId).isEqualTo(reservation.booking!!.id)
    assertThat(legacyData.leadPersonId).isEqualTo(123)

    // And
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-migrated"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(reference)
        assertThat(it["prisonerId"]).isEqualTo("FF0000FF")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["visitType"]).isEqualTo(SOCIAL.name)
        assertThat(it["visitRoom"]).isEqualTo("A1")
        assertThat(it["visitRestriction"]).isEqualTo(OPEN.name)
        assertThat(it["visitStart"]).isEqualTo(visitTime.toString())
        assertThat(it["visitStatus"]).isEqualTo(RESERVED.name)
        assertThat(it["outcomeStatus"]).isEqualTo(COMPLETED_NORMALLY.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `can migrate visit without leadVisitorId`() {

    // Given
    val createMigrateVisitRequestDto = MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )
    val jsonBody = BodyInserters.fromValue(createMigrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val reservation = reservationRepository.findByReference(reference)
    assertThat(reservation).isNotNull

    val legacyData = legacyDataRepository.findByBookingId(reservation?.booking!!.id)
    assertThat(legacyData).isNotNull
    assertThat(legacyData!!.bookingId).isEqualTo(reservation.booking!!.id)
    assertThat(legacyData.leadPersonId).isNull()
  }

  @Test
  fun `migrate visit without contact details`() {

    // Given
    val createMigrateVisitRequestDto = MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    val jsonBody = BodyInserters.fromValue(
      createMigrateVisitRequestDto
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val reservation = reservationRepository.findByReference(reference)
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull
    assertThat(reservation.booking!!.visitContact!!.name).isEqualTo("UNKNOWN")
    assertThat(reservation.booking!!.visitContact!!.telephone).isEqualTo("UNKNOWN")

    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `migrate visit when outcome status not given`() {

    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      visitRestriction = OPEN
    )

    val jsonBody = BodyInserters.fromValue(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val reservation = reservationRepository.findByReference(reference)
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull
    assertThat(reservation.booking!!.outcomeStatus).isEqualTo(NOT_RECORDED)

    // And
    verify(telemetryClient).trackEvent(
      eq("visit-scheduler-prison-visit-migrated"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(reference)
        assertThat(it["outcomeStatus"]).isEqualTo(NOT_RECORDED.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when telephone number is not given then an UNKNOWN will be migrated  `() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": "John Smith"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val reservation = reservationRepository.findByReference(getReference(responseSpec))
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull
    assertThat(reservation.booking!!.visitContact!!.telephone).isEqualTo("UNKNOWN")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when telephone number is NULL then an UNKNOWN will be migrated`() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": "John Smith",
        "telephone": null
      }     
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val reservation = reservationRepository.findByReference(getReference(responseSpec))
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull
    assertThat(reservation.booking!!.visitContact!!.telephone).isEqualTo("UNKNOWN")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name is not given then an UNKNOWN will be migrated  `() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "telephone": "1234567890"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val reservation = reservationRepository.findByReference(getReference(responseSpec))
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull
    assertThat(reservation.booking!!.visitContact!!.name).isEqualTo("UNKNOWN")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name is NULL then an UNKNOWN will be migrated`() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": null,
        "telephone": "1234567890"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val reservation = reservationRepository.findByReference(getReference(responseSpec))
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull

    assertThat(reservation.booking!!.visitContact!!.name).isEqualTo("UNKNOWN")

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact outcomeStatus is not given then an NOT_RECORDED will be migrated  `() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}"
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val reservation = reservationRepository.findByReference(getReference(responseSpec))
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull

    assertThat(reservation.booking!!.outcomeStatus).isEqualTo(NOT_RECORDED)

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact outcomeStatus is NULL then an NOT_RECORDED will be migrated`() {

    // Given
    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${RESERVED.name}",
      "visitRestriction": "${OPEN.name}",
      "outcomeStatus" : null
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val reservation = reservationRepository.findByReference(reference)
    assertThat(reservation).isNotNull
    assertThat(reservation!!.booking).isNotNull

    assertThat(reservation.booking!!.outcomeStatus).isEqualTo(NOT_RECORDED)

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-migrated"), any(), isNull())
  }

  @Test
  fun `migrate visit - invalid request`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      mapOf("wrongProperty" to "wrongValue")
    )

    // When
    val responseSpec = webTestClient.post().uri(TEST_END_POINT)
      .headers(roleVisitSchedulerHttpHeaders)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-bad-request-error"), any(), isNull())
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val jsonBody = BodyInserters.fromValue(createMigrateVisitRequestDto())

    // When
    val responseSpec = callMigrateVisit(authHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-scheduler-prison-visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createMigrateVisitRequestDto())

    // When
    val responseSpec = webTestClient.post().uri(TEST_END_POINT)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun getReference(responseSpec: ResponseSpec): String {
    var reference = ""
    responseSpec.expectBody()
      .jsonPath("$")
      .value<String> { json -> reference = json }
    return reference
  }

  private fun callMigrateVisit(jsonString: String): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromValue(
          jsonString
        )
      )
      .exchange()
  }

  private fun callMigrateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    jsonBody: BodyInserter<*, in ClientHttpRequest>?
  ): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(authHttpHeaders)
      .body(jsonBody)
      .exchange()
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(2021, 11, 1, 12, 30, 44)
  }
}
