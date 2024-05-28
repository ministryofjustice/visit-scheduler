package uk.gov.justice.digital.hmpps.visitscheduler.integration.migration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateLegacyContactOnVisitRequestDto.Companion.UNKNOWN_TOKEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigrateVisitRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.MigratedCancelVisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.OutcomeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.COMPLETED_NORMALLY
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.NOT_RECORDED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus.PRISONER_CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.TelemetryVisitEvents
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.STATUS_CHANGED_REASON
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType.SOCIAL
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callMigrateCancelVisit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.VisitNote
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("Migrate POST /visits")
@TestPropertySource(properties = ["migrate.max.months.in.future=6"])
class MigrateVisitTest : MigrationIntegrationTestBase() {

  @BeforeEach
  internal fun setUp() {
    prisonEntityHelper.create(PRISON_CODE, true)
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS"))
  }

  @Test
  fun `migrate visit`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(modifyDateTime = LocalDateTime.of(2022, 9, 11, 12, 30))
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
      assertThat(visit.sessionSlot.slotDate).isEqualTo(VISIT_TIME.toLocalDate())
      assertThat(visit.sessionSlot.slotStart).isEqualTo(VISIT_TIME)
      assertThat(visit.sessionSlot.slotEnd).isEqualTo(VISIT_TIME.plusHours(1))
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
      assertThat(visit.userType).isEqualTo(STAFF)

      val legacyData = legacyDataRepository.findByVisitId(visit.id)
      assertThat(legacyData).isNotNull
      if (legacyData != null) {
        assertThat(legacyData.visitId).isEqualTo(visit.id)
        assertThat(legacyData.leadPersonId).isEqualTo(123)
        assertThat(legacyData.migrateDateTime).isNotNull()
        assertThat(legacyData.migrateDateTime?.toLocalDate()).isEqualTo(LocalDate.now())
      }
      assertTelemetryClientEvents(visit, TelemetryVisitEvents.VISIT_MIGRATED_EVENT)

      val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
      assertThat(eventAuditList).hasSize(1)
      assertThat(eventAuditList[0].actionedBy).isEqualTo("Aled Evans")
      assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.MIGRATED_VISIT)
      assertThat(eventAuditList[0].createTimestamp).isEqualTo(migrateVisitRequestDto.createDateTime)
      assertThat(eventAuditList[0].userType).isEqualTo(STAFF)

      assertThat(visit.getApplications().size).isEqualTo(1)
      val application = visit.getLastApplication()!!
      assertVisitMatchesApplication(visit, application)
      assertThat(application.completed).isTrue()
      assertThat(application.reservedSlot).isTrue()
      assertThat(application.userType).isEqualTo(STAFF)
    }
  }

  @Test
  fun `migrate visit when end migrated session slot mismatch with session template - should use session template end date`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(modifyDateTime = LocalDateTime.of(2022, 9, 11, 12, 30))
    val sessionTemplate = createSessionTemplateFrom(migrateVisitRequestDto, endTime = migrateVisitRequestDto.endTimestamp.minusHours(1).toLocalTime())

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    assertThat(migrateVisitRequestDto.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    assertThat(migrateVisitRequestDto.endTimestamp.toLocalTime()).isNotEqualTo(sessionTemplate.endTime)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionSlot.slotDate).isEqualTo(migrateVisitRequestDto.endTimestamp.toLocalDate())
      assertThat(visit.sessionSlot.slotStart.toLocalTime()).isEqualTo(sessionTemplate.startTime)
      assertThat(visit.sessionSlot.slotEnd.toLocalTime()).isEqualTo(sessionTemplate.endTime)
      assertVisitMatchesApplication(visit, visit.getLastApplication()!!)
    }
  }

  @Test
  fun `migrate visit when end and start migrated session slot mismatch with session template - should use session template start and end date`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(modifyDateTime = LocalDateTime.of(2022, 9, 11, 12, 30))
    val sessionTemplate = createSessionTemplateFrom(
      migrateVisitRequestDto,
      startTime = migrateVisitRequestDto.startTimestamp.minusHours(1).toLocalTime(),
      endTime = migrateVisitRequestDto.endTimestamp.plusHours(1).toLocalTime(),
    )

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    assertThat(migrateVisitRequestDto.startTimestamp.toLocalTime()).isNotEqualTo(sessionTemplate.startTime)
    assertThat(migrateVisitRequestDto.endTimestamp.toLocalTime()).isNotEqualTo(sessionTemplate.endTime)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionSlot.slotDate).isEqualTo(migrateVisitRequestDto.endTimestamp.toLocalDate())
      assertThat(visit.sessionSlot.slotStart.toLocalTime()).isEqualTo(sessionTemplate.startTime)
      assertThat(visit.sessionSlot.slotEnd.toLocalTime()).isEqualTo(sessionTemplate.endTime)
      assertVisitMatchesApplication(visit, visit.getLastApplication()!!)
    }
  }

  @Test
  fun `migrate visit when start migrated session slot mismatch with session template - should use session template start date`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(modifyDateTime = LocalDateTime.of(2022, 9, 11, 12, 30))
    val sessionTemplate = createSessionTemplateFrom(migrateVisitRequestDto, startTime = migrateVisitRequestDto.startTimestamp.plusHours(1).toLocalTime())

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    assertThat(migrateVisitRequestDto.startTimestamp.toLocalTime()).isNotEqualTo(sessionTemplate.startTime)
    assertThat(migrateVisitRequestDto.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.sessionSlot.slotDate).isEqualTo(migrateVisitRequestDto.endTimestamp.toLocalDate())
      assertThat(visit.sessionSlot.slotStart.toLocalTime()).isEqualTo(sessionTemplate.startTime)
      assertThat(visit.sessionSlot.slotEnd.toLocalTime()).isEqualTo(sessionTemplate.endTime)
      assertVisitMatchesApplication(visit, visit.getLastApplication()!!)
    }
  }

  @Test
  fun `Migrate cancelled visit`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitStatus = CANCELLED, modifyDateTime = LocalDateTime.of(2022, 9, 11, 12, 30))
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
    val reference = getReference(responseSpec)

    val visit = visitRepository.findByReference(reference)
    assertThat(visit).isNotNull
    visit?.let {
      assertThat(visit.visitStatus).isEqualTo(CANCELLED)
      val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
      assertThat(eventAuditList[0].createTimestamp).isEqualTo(migrateVisitRequestDto.modifyDateTime)
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
      val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
      assertThat(eventAuditList).hasSize(1)
      assertThat(eventAuditList[0].actionedBy).isEqualTo("NOT_KNOWN_NOMIS")
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

      // telephone number will be null and not UNKNOWN
      assertThat(visit.visitContact!!.telephone).isNull()
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
  fun `When telephone number and contact name is NULL then an UNKNOWN will be migrated for name but telephone will be null`() {
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
      // telephone number will be null and not UNKNOWN
      assertThat(visit.visitContact!!.telephone).isNull()
      assertThat(visit.visitContact!!.name).isEqualTo(UNKNOWN_TOKEN)
    }

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-migrated"), any(), isNull())
  }

  @Test
  fun `When contact name, contact tel and  outcome status are not given then an UNKNOWN name, null telephone number and NOT_RECORDED will be migrated  `() {
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
      // telephone number will be null and not UNKNOWN
      assertThat(visit.visitContact!!.telephone).isNull()
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

    val application = createApplicationAndSave(completed = true, sessionTemplate = sessionTemplateDefault)
    val visit = createVisitAndSave(visitStatus = BOOKED, application, sessionTemplateDefault)

    val cancelVisitDto = MigratedCancelVisitDto(
      OutcomeDto(
        PRISONER_CANCELLED,
        "Prisoner got covid",
      ),
      CANCELLED_BY_BY_USER,
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
    assertThat(visitCancelled.visitNotes.size).isEqualTo(1)
    assertThat(visitCancelled.visitNotes[0].text).isEqualTo("Prisoner got covid")
    assertHelper.assertVisitCancellation(visitCancelled, cancelledBy = cancelVisitDto.actionedBy, applicationMethodType = NOT_KNOWN, expectedOutcomeStatus = PRISONER_CANCELLED)

    assertTelemetryClientEvents(visitCancelled, TelemetryVisitEvents.CANCELLED_VISIT_MIGRATED_EVENT)
    assertCancelledDomainEvent(visitCancelled)

    val eventAuditList = eventAuditRepository.findAllByBookingReference(visit.reference)
    assertThat(eventAuditList).hasSize(1)
    assertThat(eventAuditList[0].actionedBy).isEqualTo("user-2")
    assertThat(eventAuditList[0].type).isEqualTo(EventAuditType.CANCELLED_VISIT)
    assertThat(eventAuditList[0].userType).isEqualTo(STAFF)
  }

  @Test
  fun `When visit is more than the permitted months in the future - a exception is thrown`() {
    // Given

    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitStartTimeAndDate = LocalDateTime.now().plusMonths(7))

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: Could not migrate visit")
      .jsonPath("$.developerMessage").value(Matchers.startsWith("Visit more than 6 months in future, will not be migrated!"))
  }

  @Test
  fun `when visit is a day more than the permitted months in the future - an exception is thrown`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitStartTimeAndDate = LocalDateTime.now().plusMonths(6).plusDays(1))

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Migration failure: Could not migrate visit")
      .jsonPath("$.developerMessage").value(Matchers.startsWith("Visit more than 6 months in future, will not be migrated!"))
  }

  @Test
  fun `when visit is exactly equal to the permitted months in the future - visit is migrated successfully`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitStartTimeAndDate = LocalDateTime.now().plusMonths(6))
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
  }

  @Test
  fun `when visit is a day less than the permitted months in the future - visit is migrated successfully`() {
    // Given
    val migrateVisitRequestDto = createMigrateVisitRequestDto(visitStartTimeAndDate = LocalDateTime.now().plusMonths(6).minusDays(1))
    createSessionTemplateFrom(migrateVisitRequestDto)

    // When
    val responseSpec = callMigrateVisit(roleVisitSchedulerHttpHeaders, migrateVisitRequestDto)

    // Then
    responseSpec.expectStatus().isCreated
  }

  private fun assertVisitMatchesApplication(visit: Visit, application: Application) {
    assertThat(visit.reference).isNotEmpty()
    assertThat(visit.getLastApplication()?.reference).isEqualTo(application.reference)
    assertThat(visit.prisonerId).isEqualTo(application.prisonerId)
    assertThat(visit.prison.code).isEqualTo(application.prison.code)
    assertThat(visit.sessionSlot.slotStart)
      .isEqualTo(application.sessionSlot.slotStart)
    assertThat(visit.sessionSlot.slotEnd)
      .isEqualTo(application.sessionSlot.slotEnd)
    assertThat(visit.visitType).isEqualTo(application.visitType)
    assertThat(visit.visitStatus).isEqualTo(BOOKED)
    assertThat(visit.visitRestriction).isEqualTo(application.restriction)
    assertThat(visit.visitStatus).isEqualTo(BOOKED)
    assertThat(visit.visitContact?.name).isEqualTo(application.visitContact!!.name)
    assertThat(visit.visitContact?.telephone).isEqualTo(application.visitContact!!.telephone)
    assertThat(visit.visitors.size).isEqualTo(application.visitors.size)
    assertThat(visit.visitors[0].nomisPersonId).isEqualTo(application.visitors[0].nomisPersonId)
    assertThat(visit.visitors[0].visitContact).isEqualTo(application.visitors[0].contact)
    assertThat(visit.support).isEqualTo(application.support)
  }
}
