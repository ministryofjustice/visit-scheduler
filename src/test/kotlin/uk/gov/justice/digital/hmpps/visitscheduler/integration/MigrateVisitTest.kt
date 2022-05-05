package uk.gov.justice.digital.hmpps.visitscheduler.integration

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateSupportOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitorOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.visitDeleter
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.LegacyData
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

  private fun createVisitRequest(leadPersonId: Long? = null): MigrateVisitRequestDto {
    return MigrateVisitRequestDto(
      prisonId = "MDI",
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = VisitType.SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = VisitStatus.RESERVED,
      visitRestriction = VisitRestriction.OPEN,
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitors = listOf(CreateVisitorOnVisitRequestDto(123)),
      visitorSupport = listOf(CreateSupportOnVisitRequestDto("OTHER", "Some Text")),
      visitNotes = listOf(
        VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
        VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
        VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
        VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed")
      ),
      legacyData = leadPersonId?.let { CreateLegacyDataRequestDto(leadPersonId) }
    )
  }

  @Test
  fun `migrate visit`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createVisitRequest()
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference").isNotEmpty
      .jsonPath("$.prisonId").isEqualTo("MDI")
      .jsonPath("$.prisonerId").isEqualTo("FF0000FF")
      .jsonPath("$.visitRoom").isEqualTo("A1")
      .jsonPath("$.visitType").isEqualTo(VisitType.SOCIAL.name)
      .jsonPath("$.startTimestamp").isEqualTo(visitTime.toString())
      .jsonPath("$.endTimestamp").isEqualTo(visitTime.plusHours(1).toString())
      .jsonPath("$.visitStatus").isEqualTo(VisitStatus.RESERVED.name)
      .jsonPath("$.visitRestriction").isEqualTo(VisitRestriction.OPEN.name)
      .jsonPath("$.visitContact.name").isNotEmpty
      .jsonPath("$.visitContact.name").isEqualTo("John Smith")
      .jsonPath("$.visitContact.telephone").isEqualTo("01234 567890")
      .jsonPath("$.visitors.length()").isEqualTo(1)
      .jsonPath("$.visitors[0].nomisPersonId").isEqualTo(123)
      .jsonPath("$.visitorSupport.length()").isEqualTo(1)
      .jsonPath("$.visitorSupport[0].type").isEqualTo("OTHER")
      .jsonPath("$.visitorSupport[0].text").isEqualTo("Some Text")
      .jsonPath("$.createdTimestamp").isNotEmpty
      .jsonPath("$.visitNotes.length()").isEqualTo(4)
      .jsonPath("$.visitNotes[0].type").isEqualTo("VISITOR_CONCERN")
      .jsonPath("$.visitNotes[1].type").isEqualTo("VISIT_OUTCOMES")
      .jsonPath("$.visitNotes[2].type").isEqualTo("VISIT_COMMENT")
      .jsonPath("$.visitNotes[3].type").isEqualTo("STATUS_CHANGED_REASON")
      .jsonPath("$.visitNotes[0].text").isEqualTo("A visit concern")
      .jsonPath("$.visitNotes[1].text").isEqualTo("A visit outcome")
      .jsonPath("$.visitNotes[2].text").isEqualTo("A visit comment")
      .jsonPath("$.visitNotes[3].text").isEqualTo("Status has changed")
  }

  @Test
  fun `migrate visit with legacy data`() {

    // Given
    val jsonBody = BodyInserters.fromValue(
      createVisitRequest(leadPersonId = 123)
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    var reference = ""

    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.reference")
      .value<String> { json -> reference = json }

    var legacyData: LegacyData? = null
    val visit = visitRepository.findByReference(reference)
    visit?.let {
      legacyData = legacyDataRepository.findByVisitId(visit.id)
    }

    Assertions.assertThat(visit).isNotNull
    Assertions.assertThat(legacyData).isNotNull
    Assertions.assertThat(legacyData!!.visitId).isEqualTo(visit!!.id)
  }

  @Test
  fun `migrate visit - duplicates are ignored`() {

    // Given

    val createVisitRequest = MigrateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = VisitType.SOCIAL,
      visitStatus = VisitStatus.RESERVED,
      visitRestriction = VisitRestriction.OPEN,
      visitRoom = "A1",
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitors = listOf(
        CreateVisitorOnVisitRequestDto(123),
        CreateVisitorOnVisitRequestDto(123)
      ),
      visitorSupport = listOf(
        CreateSupportOnVisitRequestDto("OTHER", "Some Text"),
        CreateSupportOnVisitRequestDto("OTHER", "Some Text")
      )
    )

    val jsonBody = BodyInserters.fromValue(
      createVisitRequest
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isCreated
      .expectBody()
      .jsonPath("$.visitors.length()").isEqualTo(1)
      .jsonPath("$.visitorSupport.length()").isEqualTo(1)
  }

  @Test
  fun `migrate visit - invalid support`() {

    // Given

    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonerId = "FF0000FF",
      prisonId = "MDI",
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitType = VisitType.SOCIAL,
      visitStatus = VisitStatus.RESERVED,
      visitRestriction = VisitRestriction.OPEN,
      visitRoom = "A1",
      visitContact = CreateContactOnVisitRequestDto("John Smith", "01234 567890"),
      visitorSupport = listOf(CreateSupportOnVisitRequestDto("ANYTHINGWILLDO")),
    )

    val jsonBody = BodyInserters.fromValue(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isBadRequest
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
    val jsonBody = BodyInserters.fromValue(createVisitRequest(leadPersonId = null))

    // When
    val responseSpec = callMigrateVisit(authHttpHeaders, jsonBody)

    // Then
    responseSpec.expectStatus().isForbidden
  }

  @Test
  fun `unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(createVisitRequest(leadPersonId = null))

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
