package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.RESERVED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import java.time.LocalDateTime

private const val TEST_END_POINT = "/migrate-visits"

@DisplayName("Migrate POST /visits")
class MigrateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var visitRepository: VisitRepository

  @Autowired
  private lateinit var legacyDataRepository: LegacyDataRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @AfterEach
  internal fun deleteAllVisits() = visitDeleter(visitRepository)

  private fun createVisitRequest(telephone: String? = null): MigrateVisitRequestDto {
    return MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = RESERVED,
      visitRestriction = OPEN,
      visitContact = CreateLegacyContactOnVisitRequestDto("John Smith", telephone?.let { telephone }),
      visitors = listOf(CreateVisitorOnVisitRequestDto(123)),
      visitNotes = listOf(
        VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
        VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
        VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed")
      ),
      legacyData = CreateLegacyDataRequestDto(123)
    )
  }

  @Test
  @Transactional
  fun `migrate visit`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createVisitRequest("013448811538")
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    var reference = ""

    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$")
      .value<String> { json -> reference = json }

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {

      assertThat(visit.reference).isEqualTo(reference)
      assertThat(visit.prisonId).isEqualTo("MDI")
      assertThat(visit.prisonerId).isEqualTo("FF0000FF")
      assertThat(visit.visitRoom).isEqualTo("A1")
      assertThat(visit.visitType).isEqualTo(SOCIAL)
      assertThat(visit.visitStart).isEqualTo(visitTime.toString())
      assertThat(visit.visitEnd).isEqualTo(visitTime.plusHours(1).toString())
      assertThat(visit.visitStatus).isEqualTo(RESERVED)
      assertThat(visit.visitRestriction).isEqualTo(OPEN)
      assertThat(visit.visitContact!!.name).isNotEmpty
      assertThat(visit.visitContact!!.name).isEqualTo("John Smith")
      assertThat(visit.visitContact!!.telephone).isEqualTo("013448811538")
      assertThat(visit.createTimestamp).isNotNull()
      assertThat(visit.visitors.size).isEqualTo(1)
      assertThat(visit.visitors[0].nomisPersonId).isEqualTo(123)
      assertThat(visit.visitNotes.size).isEqualTo(4)
      assertThat(visit.visitNotes[0].type).isEqualTo(VISITOR_CONCERN)
      assertThat(visit.visitNotes[1].type).isEqualTo(VISIT_OUTCOMES)
      assertThat(visit.visitNotes[2].type).isEqualTo(VISIT_COMMENT)
      assertThat(visit.visitNotes[3].type).isEqualTo(STATUS_CHANGED_REASON)
      assertThat(visit.visitNotes[0].text).isEqualTo("A visit concern")
      assertThat(visit.visitNotes[1].text).isEqualTo("A visit outcome")
      assertThat(visit.visitNotes[2].text).isEqualTo("A visit comment")
      assertThat(visit.visitNotes[3].text).isEqualTo("Status has changed")

      val legacyData = legacyDataRepository.findByVisitId(visit.id)
      assertThat(legacyData).isNotNull
      assertThat(legacyData!!.visitId).isEqualTo(visit.id)
    }
  }

  @Test
  @Transactional
  fun `when telephone number is not given then an empty string will be migrated  `() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createVisitRequest()
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    var reference = ""

    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$")
      .value<String> { json -> reference = json }

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitContact!!.telephone).isEqualTo("")
    }
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
  }

  @Test
  fun `access forbidden when no role`() {

    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val jsonBody = BodyInserters.fromValue(createVisitRequest())

    // When
    val responseSpec = callMigrateVisit(authHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createVisitRequest())

    // When
    val responseSpec = webTestClient.post().uri(TEST_END_POINT)
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
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
