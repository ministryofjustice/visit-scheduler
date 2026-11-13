package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.node.ObjectNode
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PUT_VISIT_FROM_EXTERNAL_SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateVisitFromExternalSystem
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $PUT_VISIT_FROM_EXTERNAL_SYSTEM")
class UpdateVisitFromExternalSystemTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  private val reference = "v9-d7-ed-7u"
  private val updateVisitFromExternalSystemDto = UpdateVisitFromExternalSystemDto(
    actionedBy = "external-updating-user",
    applicationMethodType = ApplicationMethodType.EMAIL,
    userType = UserType.STAFF,
    visitRoom = "B1",
    visitType = VisitType.SOCIAL,
    visitRestriction = VisitRestriction.CLOSED,
    startTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
    endTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
    visitContact = ContactDto(
      name = "John Smith",
      telephone = "01234567890",
      email = "email@example.com",
    ),
    visitNotes = listOf(
      VisitNoteDto(
        type = VisitNoteType.VISITOR_CONCERN,
        text = "Visitor is concerned that his mother in-law is coming!",
      ),
    ),
    visitors = setOf(
      VisitorDto(nomisPersonId = 1234, visitContact = true),
      VisitorDto(nomisPersonId = 4321, visitContact = false),
    ),
    visitorSupport = VisitorSupportDto(
      description = "Visually impaired assistance",
    ),
  )

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `Access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callUpdateVisitFromExternalSystem(webTestClient, authHttpHeaders, reference, updateVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Update a visit from an external system`() {
    // Given
    val visit = visitEntityHelper.create(
      prisonCode = "MKI",
      visitRoom = "A1",
      slotDate = LocalDate.now(),
      visitStart = LocalTime.now(),
      visitEnd = LocalTime.now().plusHours(1),
      visitType = VisitType.SOCIAL,
      activePrison = true,
      createContact = true,
      createApplication = false,
    )

    // When
    val responseSpec = callUpdateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, visit.reference, updateVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity)

    // And
    assertUpdatedEvent(visit, visitDto)
  }

  @Test
  fun `Return 404 when visit not found`() {
    // When
    val responseSpec = callUpdateVisitFromExternalSystem(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      reference,
      updateVisitFromExternalSystemDto,
    )

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `Update a video visit publishes its type without changing its source`() {
    val visit = createExternalVisit(VisitType.SOCIAL_VIDEO)
    val request = updateVisitFromExternalSystemDto.copy(visitType = VisitType.SOCIAL_VIDEO)
    val response = callUpdateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, visit.reference, request)
    response.expectStatus().isOk
    val updatedVisit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)

    assertThat(updatedVisit.visitType).isEqualTo(VisitType.SOCIAL_VIDEO)
    assertThat(updatedVisit.userType).isEqualTo(UserType.STAFF)
    assertThat(updatedVisit.startTimestamp).isEqualTo(request.startTimestamp)
    assertThat(updatedVisit.visitRoom).isEqualTo(request.visitRoom)
    assertThat(updatedVisit.visitRestriction).isEqualTo(request.visitRestriction)
    assertThat(updatedVisit.visitExternalSystemDetails?.clientVisitReference).isEqualTo("external-video-reference")
    assertThat(storedVisit.visitType).isEqualTo(VisitType.SOCIAL_VIDEO)
    assertThat(storedVisit.userType).isEqualTo(UserType.STAFF)
    assertAuditEvent(updatedVisit, storedVisit)
    assertUpdatedEvent(visit, updatedVisit)
  }

  @ParameterizedTest
  @EnumSource(UserType::class)
  fun `Update an external visit records the audit identity for each user type without changing the visit source`(userType: UserType) {
    val visit = createExternalVisit(VisitType.SOCIAL)
    val request = updateVisitFromExternalSystemDto.copy(userType = userType)
    val response = callUpdateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, visit.reference, request)
    response.expectStatus().isOk
    val updatedVisit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)

    assertThat(updatedVisit.userType).isEqualTo(UserType.STAFF)
    assertThat(storedVisit.userType).isEqualTo(UserType.STAFF)
    assertAuditEvent(updatedVisit, storedVisit, request)
    assertUpdatedEvent(visit, updatedVisit)
  }

  @ParameterizedTest
  @CsvSource("PRISONER,actionedBy", "omitted,actionedBy", "null,actionedBy", "PRISONER,username", "omitted,username", "null,username")
  fun `when userType is prisoner or not supplied then updating a visit uses the prisoner ID for the audit`(userType: String, identityField: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto) as ObjectNode
    request.remove("actionedBy")
    request.put(identityField, "misleading-staff-user".repeat(4))
    when (userType) {
      "omitted" -> request.remove("userType")
      "null" -> request.putNull("userType")
      else -> request.put("userType", userType)
    }

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @CsvSource("60,actionedBy", "61,actionedBy", "60,username", "61,username")
  fun `Update an external visit accepts a 60 character staff username but rejects 61 characters`(length: Int, identityField: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(userType = UserType.STAFF)) as ObjectNode
    request.remove("actionedBy")
    request.put(identityField, "s".repeat(length))

    if (length == 60) {
      assertOptionalAuditMetadata(request)
    } else {
      assertUsernameExceeding60CharactersRejected(request, createExternalVisit(VisitType.SOCIAL))
    }
  }

  @ParameterizedTest
  @CsvSource("60,PRISONER", "61,PRISONER", "60,omitted", "61,omitted", "60,null", "61,null")
  fun `Update an external visit accepts a 60 character prisoner ID but rejects 61 characters`(length: Int, userType: String) {
    val visit = createExternalVisit(VisitType.SOCIAL, prisonerId = "p".repeat(length))
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(actionedBy = "ignored")) as ObjectNode
    when (userType) {
      "omitted" -> request.remove("userType")
      "null" -> request.putNull("userType")
      else -> request.put("userType", userType)
    }
    if (length == 60) {
      assertOptionalAuditMetadata(request, visit)
    } else {
      assertUsernameExceeding60CharactersRejected(request, visit)
    }
  }

  @ParameterizedTest
  @CsvSource("PUBLIC,actionedBy", "PUBLIC,username", "SYSTEM,actionedBy", "SYSTEM,username")
  fun `Update an external visit accepts actionedBy longer than 60 characters for public and system users`(userType: UserType, identityField: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(userType = userType)) as ObjectNode
    request.remove("actionedBy")
    request.put(identityField, "b".repeat(61))
    assertOptionalAuditMetadata(request)
  }

  private fun assertUsernameExceeding60CharactersRejected(request: ObjectNode, visit: Visit) {
    webTestClient.put().uri(PUT_VISIT_FROM_EXTERNAL_SYSTEM.replace("{reference}", visit.reference))
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.developerMessage").isEqualTo("Audit username must not exceed 60 characters")

    assertThat(testVisitRepository.findByReference(visit.reference).visitRoom).isEqualTo("A1")
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).isEmpty()
    verify(snsService, never()).sendChangedVisitBookedEvent(any())
    verify(telemetryClient, never()).trackEvent(eq("visit-booked"), any(), isNull())
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External update accepts username as an input alias and serializes actionedBy`(userType: UserType) {
    val expected = updateVisitFromExternalSystemDto.copy(actionedBy = "alias-updating-user", userType = userType)
    val request = objectMapper.valueToTree(expected) as ObjectNode
    request.remove("actionedBy")
    request.put("username", expected.actionedBy)

    val deserialized = objectMapper.treeToValue(request, UpdateVisitFromExternalSystemDto::class.java)
    assertThat(deserialized).isEqualTo(expected)
    val serialized: ObjectNode = objectMapper.valueToTree(deserialized)
    assertThat(serialized.has("username")).isFalse()
    assertThat(serialized["actionedBy"].asString()).isEqualTo(expected.actionedBy)

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(ApplicationMethodType::class)
  fun `External update audits the supplied application method`(applicationMethodType: ApplicationMethodType) {
    val visit = createExternalVisit(VisitType.SOCIAL)
    val request = updateVisitFromExternalSystemDto.copy(applicationMethodType = applicationMethodType)
    val response = callUpdateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, visit.reference, request)
    response.expectStatus().isOk
    val updatedVisit = getVisitDto(response)

    assertAuditEvent(updatedVisit, testVisitRepository.findByReference(visit.reference), request)
    assertUpdatedEvent(visit, updatedVisit)
  }

  @ParameterizedTest
  @CsvSource("actionedBy,false", "actionedBy,true", "applicationMethodType,false", "applicationMethodType,true", "userType,false", "userType,true", "all,false", "all,true")
  fun `External update defaults missing or null audit metadata independently`(field: String, explicitNull: Boolean) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(userType = UserType.PRISONER)) as ObjectNode
    val fields = if (field == "all") listOf("actionedBy", "applicationMethodType", "userType") else listOf(field)
    fields.forEach {
      if (explicitNull) request.putNull(it) else request.remove(it)
    }

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @ValueSource(strings = ["actionedBy", "applicationMethodType", "userType"])
  fun `External update accepts a single supplied audit field`(suppliedField: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(userType = UserType.PRISONER)) as ObjectNode
    listOf("actionedBy", "applicationMethodType", "userType").filter { it != suppliedField }.forEach { request.remove(it) }

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["PRISONER", "SYSTEM"])
  fun `External update supports prisoner and system actors with a defaulted identity`(userType: UserType) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(actionedBy = null, applicationMethodType = null, userType = userType)) as ObjectNode
    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External update accepts staff and public identities with a defaulted application method`(userType: UserType) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(applicationMethodType = null, userType = userType)) as ObjectNode
    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @CsvSource("STAFF,omitted", "STAFF,null", "STAFF,empty", "STAFF,blank", "PUBLIC,omitted", "PUBLIC,null", "PUBLIC,empty", "PUBLIC,blank")
  fun `External update rejects staff or public actors without a nonblank identity`(userType: UserType, identity: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto.copy(userType = userType)) as ObjectNode
    when (identity) {
      "omitted" -> request.remove("actionedBy")
      "null" -> request.putNull("actionedBy")
      "empty" -> request.put("actionedBy", "")
      "blank" -> request.put("actionedBy", " \t\n")
    }
    val visit = createExternalVisit(VisitType.SOCIAL)

    webTestClient.put().uri(PUT_VISIT_FROM_EXTERNAL_SYSTEM.replace("{reference}", visit.reference))
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.developerMessage").isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")

    assertThat(testVisitRepository.findByReference(visit.reference).visitRoom).isEqualTo("A1")
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).isEmpty()
    verify(snsService, never()).sendChangedVisitBookedEvent(any())
  }

  private fun assertOptionalAuditMetadata(request: ObjectNode, visit: Visit = createExternalVisit(VisitType.SOCIAL)) {
    val response = webTestClient.put().uri(PUT_VISIT_FROM_EXTERNAL_SYSTEM.replace("{reference}", visit.reference))
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
    response.expectStatus().isOk
    val updatedVisit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)

    assertThat(updatedVisit.userType).isEqualTo(UserType.STAFF)
    assertThat(storedVisit.userType).isEqualTo(UserType.STAFF)
    assertAuditEvent(updatedVisit, storedVisit, objectMapper.treeToValue(request, UpdateVisitFromExternalSystemDto::class.java))
    assertUpdatedEvent(visit, updatedVisit)
  }

  @ParameterizedTest
  @ValueSource(strings = ["applicationMethodType", "userType"])
  fun `External update rejects invalid audit enums`(field: String) {
    val request = objectMapper.valueToTree(updateVisitFromExternalSystemDto) as ObjectNode
    request.put(field, "INVALID")

    assertInvalidAuditMetadata(request)
  }

  private fun assertInvalidAuditMetadata(request: ObjectNode) {
    val visit = createExternalVisit(VisitType.SOCIAL)
    webTestClient.put().uri(PUT_VISIT_FROM_EXTERNAL_SYSTEM.replace("{reference}", visit.reference))
      .headers(roleVisitSchedulerHttpHeaders)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isBadRequest

    assertThat(testVisitRepository.findByReference(visit.reference).visitRoom).isEqualTo("A1")
    assertThat(eventAuditRepository.findAllByBookingReference(visit.reference)).isEmpty()
    verify(snsService, never()).sendChangedVisitBookedEvent(any())
  }

  @ParameterizedTest
  @EnumSource(VisitType::class)
  fun `External update cannot change a visit type`(existingType: VisitType) {
    val visit = createExternalVisit(existingType)
    val requestedType = if (existingType == VisitType.SOCIAL) VisitType.SOCIAL_VIDEO else VisitType.SOCIAL
    callUpdateVisitFromExternalSystem(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      visit.reference,
      updateVisitFromExternalSystemDto.copy(visitType = requestedType),
    ).expectStatus().isBadRequest

    assertThat(testVisitRepository.findByReference(visit.reference).visitType).isEqualTo(existingType)
    assertThat(testVisitRepository.findByReference(visit.reference).visitRoom).isEqualTo("A1")
    verify(snsService, never()).sendChangedVisitBookedEvent(any())
  }

  private fun createExternalVisit(type: VisitType, prisonerId: String = "testPrisonerId"): Visit {
    val visit = visitEntityHelper.create(
      prisonerId = prisonerId,
      prisonCode = "MKI",
      visitRoom = "A1",
      slotDate = LocalDate.now(),
      visitStart = LocalTime.of(9, 0),
      visitEnd = LocalTime.of(10, 0),
      visitType = type,
      activePrison = true,
      createContact = true,
      createApplication = false,
      userType = UserType.STAFF,
    )
    visitEntityHelper.createVisitExternalSystemClientReference(visit, "external-video-reference")
    return visitEntityHelper.save(visit)
  }

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit, request: UpdateVisitFromExternalSystemDto = updateVisitFromExternalSystemDto) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)
    val userType = request.userType ?: UserType.PRISONER
    val actionedBy = if (userType == UserType.PRISONER) visitDto.prisonerId else request.actionedBy
    assertThat(eventAudit.type).isEqualTo(EventAuditType.UPDATED_VISIT)
    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)
    val expectedUsername = actionedBy.takeIf { userType == UserType.STAFF || userType == UserType.PRISONER }
    assertThat(eventAudit.actionedBy.userName).isEqualTo(expectedUsername)
    assertThat(eventAudit.actionedBy.bookerReference).isEqualTo(actionedBy.takeIf { userType == UserType.PUBLIC })
    assertThat(eventAudit.applicationMethodType).isEqualTo(request.applicationMethodType ?: ApplicationMethodType.BY_PRISONER)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
  }

  private fun assertUpdatedEvent(visit: Visit, updatedVisitDto: VisitDto) {
    val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(updatedVisitDto.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(updatedVisitDto.reference)
        assertThat(it["applicationReference"]).isEqualTo(updatedVisitDto.applicationReference ?: "")
        assertThat(it["prisonerId"]).isEqualTo(updatedVisitDto.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(updatedVisitDto.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(updatedVisitDto.visitStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(updatedVisitDto.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(updatedVisitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(updatedVisitDto.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(updatedVisitDto.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(updatedVisitDto.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo((updatedVisitDto.visitContact.telephone != null).toString())
        assertThat(it["hasEmail"]).isEqualTo((updatedVisitDto.visitContact.email != null).toString())
        assertThat(it["supportRequired"]).isEqualTo(updatedVisitDto.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(updatedVisitDto.visitors.size.toString())
        val visitors = updatedVisitDto.visitors.map { visitor -> TelemetryClientService.VisitorDetails(visitor.nomisPersonId.toString(), null) }
        assertThat(it["visitors"]).isEqualTo(objectMapper.writeValueAsString(visitors))
        assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy.userName ?: eventAudit.actionedBy.bookerReference)
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
        assertThat(it["isUpdated"]).isEqualTo("true")
        assertThat(it["supportRequired"]).isEqualTo(updateVisitFromExternalSystemDto.visitorSupport?.description)
        assertThat(it["hasSessionChanged"]).isEqualTo("true")
        assertThat(it["hasDateChanged"]).isEqualTo("true")
        assertThat(it["existingVisitSession"]).isEqualTo(visit.sessionSlot.slotStart.format(datetimeFormatter))
        assertThat(it["newVisitSession"]).isEqualTo(updatedVisitDto.startTimestamp.format(datetimeFormatter))
        assertThat(it["hasVisitorsChanged"]).isEqualTo("true")
        assertThat(it["hasNeedsChanged"]).isEqualTo("true")
        assertThat(it["hasContactsChanged"]).isEqualTo("true")
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())

    verify(snsService, times(1)).sendChangedVisitBookedEvent(
      org.mockito.kotlin.check<SnsDomainEventPublishDto> {
        assertThat(it.reference).isEqualTo(updatedVisitDto.reference)
        assertThat(it.visitType).isEqualTo(updatedVisitDto.visitType)
      },
    )
  }
}
