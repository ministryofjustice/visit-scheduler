package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callMigrateCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.model.ApplicationMethod.UNKNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.NOT_RECORDED
import uk.gov.justice.digital.hmpps.visitscheduler.model.OutcomeStatus.PRISONER_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryVisitEvents
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("Migrate POST /visits")
class MigrateVisitTest : MigrationIntegrationTestBase() {

  @BeforeEach
  internal fun setUp() {
    prisonEntityHelper.create(PRISON_CODE, true)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @Test
  fun `Migrate visit`() {
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
      assertThat(visit.visitRoom).isEqualTo("A1")
      assertThat(visit.visitType).isEqualTo(SOCIAL)
      assertThat(visit.visitStart).isEqualTo(VISIT_TIME.toString())
      assertThat(visit.visitEnd).isEqualTo(VISIT_TIME.plusHours(1).toString())
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
        assertThat(legacyData.migrateDateTime).isNotNull()
        assertThat(legacyData.migrateDateTime?.toLocalDate()).isEqualTo(LocalDate.now())
      }
      assertTelemetryClientEvents(VisitDto(visit), TelemetryVisitEvents.VISIT_MIGRATED_EVENT)
    }
  }

  @Test
  fun `NOT_KNOWN_NOMIS is used for migrate visit when createdBy is not given`() {
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
      assertThat(visit.createdBy).isEqualTo("NOT_KNOWN_NOMIS")
    }
  }

  @Test
  fun `Can migrate visit without leadVisitorId`() {
    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = VISIT_TIME,
      endTimestamp = VISIT_TIME.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)
    mockApiCalls(migrateVisitRequestDto.prisonerId, migrateVisitRequestDto.prisonCode, housingLocations = "$migrateVisitRequestDto.prisonCode-A-1-001")

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
  fun `Migrate visit without contact details`() {
    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = VISIT_TIME,
      endTimestamp = VISIT_TIME.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)
    mockApiCalls(migrateVisitRequestDto.prisonerId, migrateVisitRequestDto.prisonCode, housingLocations = "$migrateVisitRequestDto.prisonCode-A-1-001")

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
  fun `Migrate visit without create and update Date and Time`() {
    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = VISIT_TIME,
      endTimestamp = VISIT_TIME.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)
    mockApiCalls(migrateVisitRequestDto.prisonerId, migrateVisitRequestDto.prisonCode, housingLocations = "$migrateVisitRequestDto.prisonCode-A-1-001")

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
  fun `Migrate visit when outcome status not given`() {
    // Given
    val migrateVisitRequestDto = MigrateVisitRequestDto(
      prisonCode = PRISON_CODE,
      prisonerId = "FF0000FF",
      visitRoom = "A1",
      visitType = SOCIAL,
      startTimestamp = VISIT_TIME,
      endTimestamp = VISIT_TIME.plusHours(1),
      visitStatus = BOOKED,
      visitRestriction = OPEN,
    )
    createSessionTemplateFrom(migrateVisitRequestDto)
    mockApiCalls(migrateVisitRequestDto.prisonerId, migrateVisitRequestDto.prisonCode, housingLocations = "$migrateVisitRequestDto.prisonCode-A-1-001")

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
  fun `When telephone number and contact name is NULL then an UNKNOWN will be migrated`() {
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
  fun `When contact name, contact tel and  outcome status are not given then an UNKNOWN name and NOT_RECORDED will be migrated  `() {
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
  fun `When contact name contains lowercase letters then name will be migrated as provided`() {
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
  fun `When contact name is uppercase then contact name capitalised will be migrated`() {
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
  fun `When contact outcomeStatus is NULL then an NOT_RECORDED will be migrated`() {
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
  fun `Migrate visit - invalid request`() {
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
  fun `Access forbidden when no role`() {
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
  fun `Unauthorised when no token`() {
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
      CANCELLED_BY_BY_USER,
      UNKNOWN,
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
}
