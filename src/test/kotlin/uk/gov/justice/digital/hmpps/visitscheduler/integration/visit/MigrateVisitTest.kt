package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.NOT_RECORDED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TEST_END_POINT = "/migrate-visits"

@Transactional(propagation = SUPPORTS)
@DisplayName("Migrate POST /visits")
class MigrateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var legacyDataRepository: LegacyDataRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun setUp() {
    prisonEntityHelper.create("MDI", true)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  private fun createMigrateVisitRequestDto(): MigrateVisitRequestDto {
    return MigrateVisitRequestDto(
      prisonCode = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      outcomeStatus = COMPLETED_NORMALLY,
      visitRestriction = OPEN,
      visitContact = CreateLegacyContactOnVisitRequestDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true)),
      visitNotes = setOf(
        VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
        VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
        VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed")
      ),
      legacyData = CreateLegacyDataRequestDto(123),
      createDateTime = LocalDateTime.of(2022, 9, 11, 12, 30),
      modifyDateTime = LocalDateTime.of(2022, 10, 1, 12, 30)
    )
  }

  @Test
  fun `migrate visit`() {

    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()

    val jsonBody = BodyInserters.fromValue(
      migrateVisitRequestDto
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {

      assertThat(visit.reference).isEqualTo(reference)
      assertThat(visit.prison.code).isEqualTo("MDI")
      assertThat(visit.prisonerId).isEqualTo("FF0000FF")
      assertThat(visit.visitRoom).isEqualTo("A1")
      assertThat(visit.visitType).isEqualTo(SOCIAL)
      assertThat(visit.visitStart).isEqualTo(visitTime.toString())
      assertThat(visit.visitEnd).isEqualTo(visitTime.plusHours(1).toString())
      assertThat(visit.visitStatus).isEqualTo(BOOKED)
      assertThat(visit.outcomeStatus).isEqualTo(COMPLETED_NORMALLY)
      assertThat(visit.visitRestriction).isEqualTo(OPEN)
      assertThat(visit.visitContact!!.name).isEqualTo("John Smith")
      assertThat(visit.visitContact!!.telephone).isEqualTo("013448811538")
      assertThat(visit.createTimestamp).isEqualTo(migrateVisitRequestDto.createDateTime)
      assertThat(visit.modifyTimestamp).isEqualTo(migrateVisitRequestDto.modifyDateTime)
      assertThat(visit.visitors.size).isEqualTo(1)
      assertThat(visit.visitors[0].nomisPersonId).isEqualTo(123)
      assertThat(visit.visitNotes)
        .hasSize(4)
        .extracting(VisitNote::type, VisitNote::text)
        .containsExactlyInAnyOrder(
          tuple(VISITOR_CONCERN, "A visit concern"),
          tuple(VISIT_OUTCOMES, "A visit outcome"),
          tuple(VISIT_COMMENT, "A visit comment"),
          tuple(STATUS_CHANGED_REASON, "Status has changed")
        )

      val legacyData = legacyDataRepository.findByVisitId(visit.id)
      assertThat(legacyData).isNotNull
      if (legacyData != null) {
        assertThat(legacyData.visitId).isEqualTo(visit.id)
        assertThat(legacyData.leadPersonId).isEqualTo(123)
      }
    }

    // And
    verify(telemetryClient).trackEvent(
      eq("visit-migrated"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(reference)
        assertThat(it["prisonerId"]).isEqualTo("FF0000FF")
        assertThat(it["prisonId"]).isEqualTo("MDI")
        assertThat(it["visitType"]).isEqualTo(SOCIAL.name)
        assertThat(it["visitRoom"]).isEqualTo("A1")
        assertThat(it["visitRestriction"]).isEqualTo(OPEN.name)
        assertThat(it["visitStart"]).isEqualTo(visitTime.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(BOOKED.name)
        assertThat(it["outcomeStatus"]).isEqualTo(COMPLETED_NORMALLY.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `can migrate visit without leadVisitorId`() {

    // Given
    val createMigrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )
    val jsonBody = BodyInserters.fromValue(createMigrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      val legacyData = legacyDataRepository.findByVisitId(visit.id)
      assertThat(legacyData).isNotNull
      assertThat(legacyData!!.visitId).isEqualTo(visit.id)
      assertThat(legacyData.leadPersonId).isNull()
    }
  }

  @Test
  fun `migrate visit without contact details`() {

    // Given
    val createMigrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
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

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.name).isEqualTo(UNKNOWN_TOKEN)
      assertThat(visit.visitContact!!.telephone).isEqualTo(UNKNOWN_TOKEN)
    }

    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `migrate visit without create and update Date and Time`() {

    // Given
    val createMigrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
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

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.createTimestamp).isNotNull
      assertThat(visit.modifyTimestamp).isNotNull
    }

    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `migrate visit when outcome status not given`() {

    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN
    )

    val jsonBody = BodyInserters.fromValue(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.outcomeStatus).isEqualTo(NOT_RECORDED)
    }

    // And
    verify(telemetryClient).trackEvent(
      eq("visit-migrated"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(reference)
        assertThat(it["outcomeStatus"]).isEqualTo(NOT_RECORDED.name)
      },
      isNull()
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": "John Smith"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.telephone).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
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

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.telephone).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "telephone": "1234567890"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.name).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
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

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.name).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name contains lowercase letters then name will be migrated as provided`() {

    // Given
    val name = "Title-case Name"

    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": "$name",
        "telephone": "1234567890"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.name).isEqualTo(name)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name is uppercase then contact name capitalised will be migrated`() {

    // Given
    val name = "UPPERCASE NAME"
    val capitalised = "Uppercase Name"

    val jsonString = """{
      "prisonerId": "G9377GA",
      "prisonId": "MDI",
      "visitRoom": "A1",
      "startTimestamp": "$visitTime",
      "endTimestamp": "${visitTime.plusHours(1)}",
      "visitType": "${SOCIAL.name}",
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}",
      "visitContact": {
        "name": "$name",
        "telephone": "1234567890"
      }    
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.name).isEqualTo(capitalised)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}"
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated

    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.outcomeStatus).isEqualTo(NOT_RECORDED)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
      "visitStatus": "${BOOKED.name}",
      "visitRestriction": "${OPEN.name}",
      "outcomeStatus" : null
    }"""

    // When
    val responseSpec = callMigrateVisit(jsonString)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.outcomeStatus).isEqualTo(NOT_RECORDED)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
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
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
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
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
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
    val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)
  }
}
