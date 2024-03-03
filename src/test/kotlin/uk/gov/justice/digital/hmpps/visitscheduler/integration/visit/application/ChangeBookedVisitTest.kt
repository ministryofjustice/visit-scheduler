package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getApplicationChangeVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $APPLICATION_CHANGE")
class ChangeBookedVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  lateinit var bookedVisit: Visit

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var testSessionTemplateRepository: TestSessionTemplateRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  companion object {
    const val ACTIONED_BY_USER_NAME = "user-1"
  }

  @BeforeEach
  @Transactional(propagation = REQUIRES_NEW)
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplateDefault, createApplication = true)

    visitEntityHelper.createNote(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visit, name = "OTHER", details = "Some Text")

    bookedVisit = visitEntityHelper.save(visit)
  }

  @Test
  fun `application to change visit is added but not completed`() {
    // Given
    val reference = bookedVisit.reference
    val createApplicationRequest =
      createApplicationRequest(sessionTemplateReference = bookedVisit.sessionSlot.sessionTemplateReference!!)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(bookedVisit, applicationDto, createApplicationRequest, false)
    assertTelemetryData(applicationDto)
  }

  @Test
  fun `application to change visit but no slot change is not reserved`() {
    // Given
    val reference = bookedVisit.reference
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = bookedVisit.sessionSlot.sessionTemplateReference!!)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    // And

    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(bookedVisit, applicationDto, createApplicationRequest, false)
    assertTelemetryData(applicationDto)
  }

  @Test
  fun `application to change visit with session date is reserved`() {
    // Given
    val reference = bookedVisit.reference

    val newSlotDate = this.bookedVisit.sessionSlot.slotDate.plusWeeks(1)
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplateDefault.reference, slotDate = newSlotDate)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(bookedVisit, applicationDto, createApplicationRequest, true)
    assertTelemetryData(applicationDto, "visit-slot-reserved")
  }

  @Test
  fun `application to change visit with new session is reserved`() {
    // Given
    val reference = bookedVisit.reference
    val sessionTemplate = sessionTemplateEntityHelper.create(prisonCode = "DFT")
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(bookedVisit, applicationDto, createApplicationRequest, true)
    assertTelemetryData(applicationDto, eventType = "visit-slot-reserved")
  }

  @Test
  fun `changed booked visit creates new application and updates visit restriction has changed`() {
    // Given
    val reference = bookedVisit.reference
    val newRestriction = if (bookedVisit.visitRestriction == OPEN) CreateApplicationRestriction.CLOSED else CreateApplicationRestriction.OPEN
    val createApplicationRequest = createApplicationRequest(visitRestriction = newRestriction)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(bookedVisit, applicationDto, createApplicationRequest, true)
    assertTelemetryData(applicationDto, eventType = "visit-slot-reserved")
  }

  @Test
  fun `application to change visit sends correct telemetry data`() {
    // Given
    val bookingReference = bookedVisit.reference
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = bookedVisit.sessionSlot.sessionTemplateReference!!)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, bookingReference)

    // Then
    val returnResult = getResult(responseSpec)

    // And
    val applicationDto = getApplicationDto(returnResult)
    assertTelemetryData(applicationDto)
  }

  @Test
  fun `change visit - invalid request`() {
    // Given
    val reference = bookedVisit.reference

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient = webTestClient, authHttpHeaders = roleVisitSchedulerHttpHeaders, reference = reference)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `change visit - access forbidden when no role`() {
    // Given
    val incorrectAuthHeaders = setAuthorisation(roles = listOf())
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference)
    val reference = bookedVisit.reference

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, incorrectAuthHeaders, createApplicationRequest, reference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `change visit - unauthorised when no token`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference)

    val jsonBody = BodyInserters.fromValue(createApplicationRequest)
    val reference = bookedVisit.reference

    // When
    val responseSpec = webTestClient.put().uri(getApplicationChangeVisitUrl(reference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `change visit - not found`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference)
    val applicationReference = "IM NOT HERE"

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `change visit that has already expired returns bad request`() {
    // Given
    val visitStart = LocalDateTime.of((LocalDateTime.now().year - 1), 11, 1, 12, 30, 44)
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = visitStart.toLocalDate(), visitStart = visitStart.toLocalTime(), sessionTemplate = sessionTemplateDefault)
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplateDefault.reference)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, expiredVisit.reference)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with reference - ${expiredVisit.reference} is in the past, it cannot be changed")
  }

  private fun assertTelemetryData(
    applicationDto: ApplicationDto,
    eventType: String = "visit-changed",
  ) {
    verify(telemetryClient, times(1)).trackEvent(eq(eventType), any(), isNull())

    assertThat(applicationDto).isNotNull

    val application = getApplication(applicationDto)
    assertThat(application).isNotNull
    application?.let {
      val visitStartStr = formatStartSlotDateTimeToString(application.sessionSlot)
      verify(telemetryClient).trackEvent(
        eq(eventType),
        org.mockito.kotlin.check {
          assertThat(it["reference"]).isEqualTo(bookedVisit.reference)
          assertThat(it["applicationReference"]).isNotEqualTo(bookedVisit.getLastApplication()?.reference)
          assertThat(it["applicationReference"]).isEqualTo(applicationDto.reference)
          assertThat(it["prisonerId"]).isEqualTo(applicationDto.prisonerId)
          assertThat(it["prisonId"]).isEqualTo(applicationDto.prisonCode)
          assertThat(it["visitType"]).isEqualTo(applicationDto.visitType.name)
          assertThat(it["visitRestriction"]).isEqualTo(applicationDto.visitRestriction.name)
          assertThat(it["visitStart"]).isEqualTo(visitStartStr)
          assertThat(it["reserved"]).isEqualTo(applicationDto.reserved.toString())
        },
        isNull(),
      )
    }
  }

  private fun createApplicationRequest(
    prisonerId: String = "testPrisonerId",
    slotDate: LocalDate = bookedVisit.sessionSlot.slotDate,
    visitRestriction: CreateApplicationRestriction = CreateApplicationRestriction.OPEN,
    sessionTemplateReference: String = bookedVisit.sessionSlot.sessionTemplateReference!!,
  ): CreateApplicationDto {
    return CreateApplicationDto(
      prisonerId = prisonerId,
      sessionDate = slotDate,
      applicationRestriction = visitRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = ACTIONED_BY_USER_NAME,
      sessionTemplateReference = sessionTemplateReference,
    )
  }

  private fun getApplication(dto: ApplicationDto): Application? {
    return testApplicationRepository.findByReference(dto.reference)
  }

  private fun getResult(responseSpec: ResponseSpec): EntityExchangeResult<ByteArray> {
    return responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()
  }

  private fun getApplicationDto(returnResult: EntityExchangeResult<ByteArray>): ApplicationDto {
    return objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)
  }

  private fun assertApplicationDetails(
    lastBooking: Visit,
    returnedApplication: ApplicationDto,
    createApplicationRequest: CreateApplicationDto,
    reserved: Boolean,
  ) {
    val sessionTemplate = testSessionTemplateRepository.findByReference(createApplicationRequest.sessionTemplateReference)

    val visitConnectedToApplication = testApplicationRepository.findVisitByApplicationReference(returnedApplication.reference)
    assertThat(visitConnectedToApplication?.getApplications()?.size).isEqualTo(2)
    assertThat(visitConnectedToApplication).isNotNull
    assertThat(visitConnectedToApplication?.reference).isEqualTo(lastBooking.reference)
    assertThat(returnedApplication.reference).isEqualTo(visitConnectedToApplication?.getLastApplication()?.reference)
    assertThat(returnedApplication.prisonerId).isEqualTo(lastBooking.prisonerId)
    assertThat(returnedApplication.prisonCode).isEqualTo(sessionTemplate.prison.code)
    assertThat(returnedApplication.startTimestamp.toLocalDate()).isEqualTo(createApplicationRequest.sessionDate)
    assertThat(returnedApplication.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    assertThat(returnedApplication.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    assertThat(returnedApplication.visitType).isEqualTo(lastBooking.visitType)
    assertThat(returnedApplication.reserved).isEqualTo(reserved)
    assertThat(returnedApplication.completed).isFalse()
    assertThat(returnedApplication.visitRestriction.name).isEqualTo(createApplicationRequest.applicationRestriction.name)
    assertThat(returnedApplication.sessionTemplateReference).isEqualTo(createApplicationRequest.sessionTemplateReference)

    createApplicationRequest.visitContact?.let {
      assertThat(returnedApplication.visitContact!!.name).isEqualTo(it.name)
      assertThat(returnedApplication.visitContact!!.telephone).isEqualTo(it.telephone)
    } ?: run {
      assertThat(returnedApplication.visitContact!!.name).isEqualTo(lastBooking.visitContact!!.name)
      assertThat(returnedApplication.visitContact!!.telephone).isEqualTo(lastBooking.visitContact!!.telephone)
    }

    val visitorsDtoList = returnedApplication.visitors.toList()
    createApplicationRequest.visitors.let {
      assertThat(returnedApplication.visitors.size).isEqualTo(it.size)
      it.forEachIndexed { index, visitorDto ->
        assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitorDto.nomisPersonId)
        assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitorDto.visitContact)
      }
    }

    val supportDtoList = returnedApplication.visitorSupport.toList()
    createApplicationRequest.visitorSupport?.let {
      assertThat(returnedApplication.visitorSupport.size).isEqualTo(it.size)
      it.forEachIndexed { index, supportDto ->
        assertThat(supportDtoList[index].type).isEqualTo(supportDto.type)
        assertThat(supportDtoList[index].text).isEqualTo(supportDto.text)
      }
    } ?: run {
      assertThat(returnedApplication.visitorSupport.size).isEqualTo(lastBooking.support.size)
      lastBooking.support.forEachIndexed { index, support ->
        assertThat(supportDtoList[index].type).isEqualTo(support.type)
        assertThat(supportDtoList[index].text).isEqualTo(support.text)
      }
    }

    assertThat(returnedApplication.createdTimestamp).isNotNull()
  }
}
