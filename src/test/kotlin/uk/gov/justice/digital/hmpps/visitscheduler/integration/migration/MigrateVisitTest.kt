package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

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
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyDataRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callMigrateCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.NOT_RECORDED
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.PRISONER_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.repository.LegacyDataRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.VisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TEST_END_POINT = "/migrate-visits"

private const val PRISON_CODE = "MDI"
private const val cancelledByByUser = "user-2"

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
    prisonEntityHelper.create(PRISON_CODE, true)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @Test
  fun `migrate visit`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto()
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.reference).isEqualTo(reference)
      assertThat(visit.prison.code).isEqualTo(PRISON_CODE)
      assertThat(visit.prisonerId).isEqualTo("FF0000FF")
      assertThat(visit.capacityGroup).isEqualTo("A1")
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
          tuple(STATUS_CHANGED_REASON, "Status has changed"),
        )
      assertThat(visit.createdBy).isEqualTo("Aled Evans")

      val legacyData = legacyDataRepository.findByVisitId(visit.id)
      assertThat(legacyData).isNotNull
      if (legacyData != null) {
        assertThat(legacyData.visitId).isEqualTo(visit.id)
        assertThat(legacyData.leadPersonId).isEqualTo(123)
        assertThat(legacyData.visitRoom).isEqualTo("A1")
      }
      assertTelemetryClientEvents(VisitDto(visit), TelemetryVisitEvents.VISIT_MIGRATED_EVENT)
    }
  }

  private fun createSessionTemplateFrom(migrateVisitRequestDto: MigrateVisitRequestDto) {
    sessionTemplateEntityHelper.create(
      validFromDate = migrateVisitRequestDto.startTimestamp.toLocalDate().minusDays(1),
      prisonCode = migrateVisitRequestDto.prisonCode,
      dayOfWeek = migrateVisitRequestDto.startTimestamp.dayOfWeek,
      capacityGroup = migrateVisitRequestDto.visitRoom,
      startTime = migrateVisitRequestDto.startTimestamp.toLocalTime(),
      endTime = migrateVisitRequestDto.endTimestamp.toLocalTime(),
    )
  }

  @Test
  fun `Capacity group - migrate visit has null capacity group if visit is in the past`() {
    // Given

    val visitStartTimeAndDate = LocalDateTime.now().minusDays(95)

    val migrateVisitRequestDto = createMigrateVisitRequestDto(
      visitStartTimeAndDate = visitStartTimeAndDate,
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.capacityGroup).isNull()
    }
  }

  @Test
  fun `client id is used for migrate visit when createdBy is not given`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(actionedBy = null)
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val visit = visitRepository.findByReference(getReference(responseSpec))
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.createdBy).isEqualTo("AUTH_ADM")
    }
  }

  @Test
  fun `can migrate visit without leadVisitorId`() {
    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = visitTime,
      endTimestamp = visitTime.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `when telephone number and contact name is NULL then an UNKNOWN will be migrated`() {
    // Given
    val migrateVisitDto = createMigrateVisitRequestDto()
    createSessionTemplateFrom(migrateVisitDto)

    val jsonString = """{
      "prisonerId": "${migrateVisitDto.prisonerId}",
      "prisonId": "${migrateVisitDto.prisonCode}",
      "visitRoom": "${migrateVisitDto.visitRoom}",
      "startTimestamp": "${migrateVisitDto.startTimestamp}",
      "endTimestamp": "${migrateVisitDto.endTimestamp}",
      "visitType": "${migrateVisitDto.visitType.name}",
      "visitStatus": "${migrateVisitDto.visitStatus.name}",
      "visitRestriction": "${migrateVisitDto.visitRestriction.name}",
      "visitContact": {
        "name": null,
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
      assertThat(visit.visitContact!!.name).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name, contact tel and  outcome status are not given then an UNKNOWN name and NOT_RECORDED will be migrated  `() {
    // Given
    val migrateVisitDto = createMigrateVisitRequestDto()
    createSessionTemplateFrom(migrateVisitDto)

    val jsonString = """{
      "prisonerId": "${migrateVisitDto.prisonerId}",
      "prisonId": "${migrateVisitDto.prisonCode}",
      "visitRoom": "${migrateVisitDto.visitRoom}",
      "startTimestamp": "${migrateVisitDto.startTimestamp}",
      "endTimestamp": "${migrateVisitDto.endTimestamp}",
      "visitType": "${migrateVisitDto.visitType.name}",
      "visitStatus": "${migrateVisitDto.visitStatus.name}",
      "visitRestriction": "${migrateVisitDto.visitRestriction.name}",
      "visitContact": {
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
      assertThat(visit.visitContact!!.telephone).isEqualTo(UNKNOWN_TOKEN)
      assertThat(visit.outcomeStatus).isEqualTo(NOT_RECORDED)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `when contact name contains lowercase letters then name will be migrated as provided`() {
    // Given
    val name = "Title-case Name"

    val migrateVisitRequestDto = createMigrateVisitRequestDto(contactName = name)
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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

    val migrateVisitRequestDto = createMigrateVisitRequestDto(contactName = name)
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
  fun `when contact outcomeStatus is NULL then an NOT_RECORDED will be migrated`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(outcomeStatus = null)
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

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
      mapOf("wrongProperty" to "wrongValue"),
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
    val createMigrateVisitRequestDto = createMigrateVisitRequestDto()

    // When
    val responseSpec = callMigrateVisit(authHttpHeaders, createMigrateVisitRequestDto)

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

  @Test
  fun `cancel visit migrated by reference -  with outcome and outcome text`() {
    // Given
    val visit = visitEntityHelper.create(visitStatus = BOOKED)

    val cancelVisitDto = CancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      cancelledByByUser,
    )
    val reference = visit.reference

    // When
    val responseSpec = callMigrateCancelVisit(webTestClient, setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")), reference, cancelVisitDto)

    // Then
    val returnResult = responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()

    // And
    val visitCancelled = objectMapper.readValue(returnResult.responseBody, VisitDto::class.java)
    assertVisitCancellation(visitCancelled, PRISONER_CANCELLED, cancelVisitDto.actionedBy)
    assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertThat(visitCancelled.createdBy).isEqualTo(visit.createdBy)
    assertThat(visitCancelled.updatedBy).isEqualTo(visit.updatedBy)

    assertTelemetryClientEvents(visitCancelled, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)
    assertCancelledDomainEvent(visitCancelled)
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
          jsonString,
        ),
      )
      .exchange()
  }

  private fun callMigrateVisit(
    authHttpHeaders: (HttpHeaders) -> Unit,
    migrateVisitRequestDto: MigrateVisitRequestDto,
  ): ResponseSpec {
    return webTestClient.post().uri(TEST_END_POINT)
      .headers(authHttpHeaders)
      .body(BodyInserters.fromValue(migrateVisitRequestDto))
      .exchange()
  }

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)
  }

  fun assertTelemetryClientEvents(
    cancelledVisit: VisitDto,
    type: TelemetryVisitEvents,
  ) {
    verify(telemetryClient).trackEvent(
      eq(type.eventName),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
        assertThat(it["applicationReference"]).isEqualTo(cancelledVisit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(cancelledVisit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(cancelledVisit.prisonCode)
        assertThat(it["visitType"]).isEqualTo(cancelledVisit.visitType.name)
        assertThat(it["capacityGroup"]).isEqualTo(cancelledVisit.capacityGroup)
        assertThat(it["visitRestriction"]).isEqualTo(cancelledVisit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(cancelledVisit.visitStatus.name)
        assertThat(it["outcomeStatus"]).isEqualTo(cancelledVisit.outcomeStatus!!.name)
      },
      isNull(),
    )

    val eventsMap = mutableMapOf(
      "reference" to cancelledVisit.reference,
      "applicationReference" to cancelledVisit.applicationReference,
      "prisonerId" to cancelledVisit.prisonerId,
      "prisonId" to cancelledVisit.prisonCode,
      "visitType" to cancelledVisit.visitType.name,
      "capacityGroup" to cancelledVisit.capacityGroup,
      "visitRestriction" to cancelledVisit.visitRestriction.name,
      "visitStart" to cancelledVisit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
      "visitStatus" to cancelledVisit.visitStatus.name,
      "outcomeStatus" to cancelledVisit.outcomeStatus!!.name,
    )
    verify(telemetryClient, times(1)).trackEvent(type.eventName, eventsMap, null)
  }

  fun assertCancelledDomainEvent(
    cancelledVisit: VisitDto,
  ) {
    verify(telemetryClient).trackEvent(
      eq("prison-visit.cancelled-domain-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(cancelledVisit.reference)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("prison-visit.cancelled-domain-event"), any(), isNull())
  }

  fun assertVisitCancellation(
    cancelledVisit: VisitDto,
    expectedOutcomeStatus: OutcomeStatus,
    cancelledBy: String,
  ) {
    assertThat(cancelledVisit.visitStatus).isEqualTo(VisitStatus.CANCELLED)
    assertThat(cancelledVisit.outcomeStatus).isEqualTo(expectedOutcomeStatus)
    assertThat(cancelledVisit.cancelledBy).isEqualTo(cancelledBy)
  }
}

fun createMigrateVisitRequestDto(
  actionedBy: String? = "Aled Evans",
  visitStartTimeAndDate: LocalDateTime = MigrateVisitTest.visitTime,
  outcomeStatus: OutcomeStatus? = COMPLETED_NORMALLY,
  contactName: String? = "John Smith",
): MigrateVisitRequestDto {
  return MigrateVisitRequestDto(
    prisonCode = PRISON_CODE,
    prisonerId = "FF0000FF",
    visitRoom = "A1",
    visitType = SOCIAL,
    startTimestamp = visitStartTimeAndDate,
    endTimestamp = visitStartTimeAndDate.plusHours(1),
    visitStatus = BOOKED,
    outcomeStatus = outcomeStatus,
    visitRestriction = OPEN,
    visitContact = CreateLegacyContactOnVisitRequestDto(contactName!!, "013448811538"),
    visitors = setOf(VisitorDto(123, true)),
    visitNotes = setOf(
      VisitNoteDto(type = VISITOR_CONCERN, "A visit concern"),
      VisitNoteDto(type = VISIT_OUTCOMES, "A visit outcome"),
      VisitNoteDto(type = VISIT_COMMENT, "A visit comment"),
      VisitNoteDto(type = STATUS_CHANGED_REASON, "Status has changed"),
    ),
    legacyData = CreateLegacyDataRequestDto(123),
    createDateTime = LocalDateTime.of(2022, 9, 11, 12, 30),
    modifyDateTime = LocalDateTime.of(2022, 10, 1, 12, 30),
    actionedBy = actionedBy,
  )
}
