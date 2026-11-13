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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.node.ObjectNode
import uk.gov.justice.digital.hmpps.visitscheduler.controller.POST_VISIT_FROM_EXTERNAL_SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateVisitFromExternalSystem
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callGet
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callPostByJsonString
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $POST_VISIT_FROM_EXTERNAL_SYSTEM")
class CreateVisitFromExternalSystemTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  private val prisonId = "MKI"
  private val createVisitFromExternalSystemDto = CreateVisitFromExternalSystemDto(
    actionedBy = "external-booking-user",
    applicationMethodType = ApplicationMethodType.PHONE,
    userType = UserType.STAFF,
    prisonerId = "AF34567G",
    prisonId = prisonId,
    clientName = "client-name",
    clientVisitReference = "client-visit-reference-1",
    visitRoom = "A1",
    visitType = VisitType.SOCIAL,
    visitRestriction = VisitRestriction.OPEN,
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
    createDateTime = LocalDateTime.parse("2018-12-01T13:45:00"),
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

    prisonEntityHelper.create(prisonCode = prisonId)
  }

  @Test
  fun `Access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callCreateVisitFromExternalSystem(webTestClient, authHttpHeaders, createVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Create a visit from an external system`() {
    // Given

    // When
    val responseSpec = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, createVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertThat(visitEntity.userType).isEqualTo(UserType.PRISONER)
    assertAuditEvent(visitDto, visitEntity)

    // And
    assertBookedEvent(visitDto)
  }

  @Test
  fun `External video visit is stored audited and published with its visit type`() {
    val request = createVisitFromExternalSystemDto.copy(visitType = VisitType.SOCIAL_VIDEO)
    val response = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, request)
    response.expectStatus().isOk
    val visit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)

    assertThat(visit.visitType).isEqualTo(VisitType.SOCIAL_VIDEO)
    assertThat(visit.userType).isEqualTo(UserType.PRISONER)
    assertThat(storedVisit.visitType).isEqualTo(VisitType.SOCIAL_VIDEO)
    assertThat(storedVisit.userType).isEqualTo(UserType.PRISONER)
    assertThat(visit.sessionTemplateReference).isNull()
    assertThat(visit.visitExternalSystemDetails?.clientVisitReference).isEqualTo(request.clientVisitReference)
    assertAuditEvent(visit, storedVisit)
    assertBookedEvent(visit)

    callGet(webTestClient, "/visits/external-system/${request.clientVisitReference}", roleVisitSchedulerHttpHeaders)
      .expectStatus().isOk
      .expectBody().jsonPath("$[0]").isEqualTo(visit.reference)
    callGet(webTestClient, "/visits/${visit.reference}", roleVisitSchedulerHttpHeaders)
      .expectStatus().isOk
      .expectBody().jsonPath("$.visitType").isEqualTo("SOCIAL_VIDEO")
  }

  @ParameterizedTest
  @EnumSource(VisitType::class)
  fun `Supplied actor type does not change the external visit source`(visitType: VisitType) {
    val request = createVisitFromExternalSystemDto.copy(visitType = visitType)
    val response = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, request)
    response.expectStatus().isOk
    val visit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)
    assertThat(visit.userType).isEqualTo(UserType.PRISONER)
    assertThat(storedVisit.userType).isEqualTo(UserType.PRISONER)
    assertAuditEvent(visit, storedVisit)
    assertBookedEvent(visit)
  }

  @ParameterizedTest
  @EnumSource(UserType::class)
  fun `Create an external visit records the audit identity for each user type`(userType: UserType) {
    val request = createVisitFromExternalSystemDto.copy(userType = userType)
    val response = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, request)
    response.expectStatus().isOk
    val visit = getVisitDto(response)
    assertThat(visit.userType).isEqualTo(UserType.PRISONER)
    val storedVisit = testVisitRepository.findByReference(visit.reference)
    assertThat(storedVisit.userType).isEqualTo(UserType.PRISONER)
    assertAuditEvent(visit, storedVisit, request)
    assertBookedEvent(visit)
  }

  @ParameterizedTest
  @CsvSource("PRISONER,actionedBy", "omitted,actionedBy", "null,actionedBy", "PRISONER,username", "omitted,username", "null,username")
  fun `when userType is prisoner or not supplied then creating a visit uses the prisoner ID for the audit`(userType: String, identityField: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto) as ObjectNode
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
  fun `Create an external visit accepts a 60 character staff username but rejects 61 characters`(length: Int, identityField: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(userType = UserType.STAFF)) as ObjectNode
    request.remove("actionedBy")
    request.put(identityField, "s".repeat(length))

    if (length == 60) {
      assertOptionalAuditMetadata(request)
    } else {
      assertUsernameExceeding60CharactersRejected(request)
    }
  }

  @ParameterizedTest
  @CsvSource("60,PRISONER", "61,PRISONER", "60,omitted", "61,omitted", "60,null", "61,null")
  fun `Create an external visit accepts a 60 character prisoner ID but rejects 61 characters`(length: Int, userType: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(prisonerId = "p".repeat(length), actionedBy = "ignored")) as ObjectNode
    when (userType) {
      "omitted" -> request.remove("userType")
      "null" -> request.putNull("userType")
      else -> request.put("userType", userType)
    }
    if (length == 60) {
      assertOptionalAuditMetadata(request)
    } else {
      assertUsernameExceeding60CharactersRejected(request)
    }
  }

  @ParameterizedTest
  @CsvSource("PUBLIC,actionedBy", "PUBLIC,username", "SYSTEM,actionedBy", "SYSTEM,username")
  fun `Create an external visit accepts actionedBy longer than 60 characters for public and system users`(userType: UserType, identityField: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(userType = userType)) as ObjectNode
    request.remove("actionedBy")
    request.put(identityField, "b".repeat(61))
    assertOptionalAuditMetadata(request)
  }

  private fun assertUsernameExceeding60CharactersRejected(request: ObjectNode) {
    callPostByJsonString(objectMapper.writeValueAsString(request), webTestClient, POST_VISIT_FROM_EXTERNAL_SYSTEM, roleVisitSchedulerHttpHeaders)
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.developerMessage").isEqualTo("Audit username must not exceed 60 characters")

    assertThat(testVisitRepository.count()).isZero()
    assertThat(eventAuditRepository.count()).isZero()
    verify(snsService, never()).sendVisitBookedEvent(any())
    verify(telemetryClient, never()).trackEvent(eq("visit-booked"), any(), isNull())
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External creation accepts username as an input alias and serializes actionedBy`(userType: UserType) {
    val expected = createVisitFromExternalSystemDto.copy(actionedBy = "alias-booking-user", userType = userType)
    val request = objectMapper.valueToTree(expected) as ObjectNode
    request.remove("actionedBy")
    request.put("username", expected.actionedBy)

    val deserialized = objectMapper.treeToValue(request, CreateVisitFromExternalSystemDto::class.java)
    assertThat(deserialized).isEqualTo(expected)
    val serialized: ObjectNode = objectMapper.valueToTree(deserialized)
    assertThat(serialized.has("username")).isFalse()
    assertThat(serialized["actionedBy"].asString()).isEqualTo(expected.actionedBy)

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(ApplicationMethodType::class)
  fun `External creation audits the supplied application method`(applicationMethodType: ApplicationMethodType) {
    val request = createVisitFromExternalSystemDto.copy(applicationMethodType = applicationMethodType)
    val response = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, request)
    response.expectStatus().isOk
    val visit = getVisitDto(response)
    assertAuditEvent(visit, testVisitRepository.findByReference(visit.reference), request)
    assertBookedEvent(visit)
  }

  @ParameterizedTest
  @CsvSource("actionedBy,false", "actionedBy,true", "applicationMethodType,false", "applicationMethodType,true", "userType,false", "userType,true", "all,false", "all,true")
  fun `External creation defaults missing or null audit metadata independently`(field: String, explicitNull: Boolean) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(userType = UserType.PRISONER)) as ObjectNode
    val fields = if (field == "all") listOf("actionedBy", "applicationMethodType", "userType") else listOf(field)
    fields.forEach {
      if (explicitNull) request.putNull(it) else request.remove(it)
    }

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @ValueSource(strings = ["actionedBy", "applicationMethodType", "userType"])
  fun `External creation accepts a single supplied audit field`(suppliedField: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(userType = UserType.PRISONER)) as ObjectNode
    listOf("actionedBy", "applicationMethodType", "userType").filter { it != suppliedField }.forEach { request.remove(it) }

    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["PRISONER", "SYSTEM"])
  fun `External creation supports prisoner and system actors with a defaulted identity`(userType: UserType) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(actionedBy = null, applicationMethodType = null, userType = userType)) as ObjectNode
    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @EnumSource(value = UserType::class, names = ["STAFF", "PUBLIC"])
  fun `External creation accepts staff and public identities with a defaulted application method`(userType: UserType) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(applicationMethodType = null, userType = userType)) as ObjectNode
    assertOptionalAuditMetadata(request)
  }

  @ParameterizedTest
  @CsvSource("STAFF,omitted", "STAFF,null", "STAFF,empty", "STAFF,blank", "PUBLIC,omitted", "PUBLIC,null", "PUBLIC,empty", "PUBLIC,blank")
  fun `External creation rejects staff or public actors without a nonblank identity`(userType: UserType, identity: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto.copy(userType = userType)) as ObjectNode
    when (identity) {
      "omitted" -> request.remove("actionedBy")
      "null" -> request.putNull("actionedBy")
      "empty" -> request.put("actionedBy", "")
      "blank" -> request.put("actionedBy", " \t\n")
    }

    callPostByJsonString(objectMapper.writeValueAsString(request), webTestClient, POST_VISIT_FROM_EXTERNAL_SYSTEM, roleVisitSchedulerHttpHeaders)
      .expectStatus().isBadRequest
      .expectBody().jsonPath("$.developerMessage").isEqualTo("actionedBy must be provided for STAFF or PUBLIC users")

    assertThat(testVisitRepository.count()).isZero()
    assertThat(eventAuditRepository.count()).isZero()
    verify(snsService, never()).sendVisitBookedEvent(any())
  }

  private fun assertOptionalAuditMetadata(request: ObjectNode) {
    val response = callPostByJsonString(objectMapper.writeValueAsString(request), webTestClient, POST_VISIT_FROM_EXTERNAL_SYSTEM, roleVisitSchedulerHttpHeaders)
    response.expectStatus().isOk
    val visit = getVisitDto(response)
    val storedVisit = testVisitRepository.findByReference(visit.reference)

    assertThat(visit.userType).isEqualTo(UserType.PRISONER)
    assertThat(storedVisit.userType).isEqualTo(UserType.PRISONER)
    assertAuditEvent(visit, storedVisit, objectMapper.treeToValue(request, CreateVisitFromExternalSystemDto::class.java))
    assertBookedEvent(visit)
  }

  @ParameterizedTest
  @ValueSource(strings = ["applicationMethodType", "userType"])
  fun `External creation rejects invalid audit enums`(field: String) {
    val request = objectMapper.valueToTree(createVisitFromExternalSystemDto) as ObjectNode
    request.put(field, "INVALID")

    callPostByJsonString(objectMapper.writeValueAsString(request), webTestClient, POST_VISIT_FROM_EXTERNAL_SYSTEM, roleVisitSchedulerHttpHeaders)
      .expectStatus().isBadRequest

    assertThat(testVisitRepository.count()).isZero()
    assertThat(eventAuditRepository.count()).isZero()
    verify(snsService, never()).sendVisitBookedEvent(any())
  }

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit, request: CreateVisitFromExternalSystemDto = createVisitFromExternalSystemDto) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)
    val userType = request.userType ?: UserType.PRISONER
    val actionedBy = if (userType == UserType.PRISONER) visitDto.prisonerId else request.actionedBy
    assertThat(eventAudit.type).isEqualTo(EventAuditType.BOOKED_VISIT)
    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)
    val expectedUsername = actionedBy.takeIf { userType == UserType.STAFF || userType == UserType.PRISONER }
    assertThat(eventAudit.actionedBy.userName).isEqualTo(expectedUsername)
    assertThat(eventAudit.actionedBy.bookerReference).isEqualTo(actionedBy.takeIf { userType == UserType.PUBLIC })
    assertThat(eventAudit.applicationMethodType).isEqualTo(request.applicationMethodType ?: ApplicationMethodType.BY_PRISONER)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
  }

  private fun assertBookedEvent(visit: VisitDto) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(visit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo((visit.visitContact.telephone != null).toString())
        assertThat(it["hasEmail"]).isEqualTo((visit.visitContact.email != null).toString())
        assertThat(it["supportRequired"]).isEqualTo(visit.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(visit.visitors.size.toString())
        val visitors = visit.visitors.map { visitor -> TelemetryClientService.VisitorDetails(visitor.nomisPersonId.toString(), null) }
        assertThat(it["visitors"]).isEqualTo(objectMapper.writeValueAsString(visitors))
        assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy.userName ?: eventAudit.actionedBy.bookerReference)
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())

    verify(snsService, times(1)).sendVisitBookedEvent(
      org.mockito.kotlin.check<SnsDomainEventPublishDto> {
        assertThat(it.reference).isEqualTo(visit.reference)
        assertThat(it.visitType).isEqualTo(visit.visitType)
      },
    )
  }
}
