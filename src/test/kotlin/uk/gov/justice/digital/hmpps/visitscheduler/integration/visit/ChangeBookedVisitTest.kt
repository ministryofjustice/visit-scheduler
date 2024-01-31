package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getApplicationChangeVisitUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $APPLICATION_CHANGE")
class ChangeBookedVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  lateinit var bookedVisit: Visit

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  companion object {
    const val actionedByUserName = "user-1"
  }

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = startDate, sessionTemplate = sessionTemplate, createApplication = true)

    visitEntityHelper.createNote(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visit, name = "OTHER", details = "Some Text")

    bookedVisit = visitEntityHelper.save(visit)
  }

  private fun createApplicationRequest(
    prisonerId: String = "FF0000AA",
    slotDate: LocalDate = bookedVisit.sessionSlot.slotDate,
    visitRestriction: VisitRestriction = OPEN,
    sessionTemplateReference: String = bookedVisit.sessionSlot.sessionTemplateReference!!,
  ): CreateApplicationDto {
    return CreateApplicationDto(
      prisonerId = prisonerId,
      sessionDate = slotDate,
      visitRestriction = visitRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = actionedByUserName,
      sessionTemplateReference = sessionTemplateReference,
    )
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

    // And

    val applicationDto = getApplicationDto(returnResult)
    assertThat(applicationDto.completed).isFalse()
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
    assertThat(applicationDto.reserved).isFalse()
    assertTelemetryData(applicationDto)
  }

  @Test
  fun `application to change visit with session date is reserved`() {
    // Given
    val reference = bookedVisit.reference

    val newSlotDate = this.bookedVisit.sessionSlot.slotDate.plusWeeks(1)
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference, slotDate = newSlotDate)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertThat(applicationDto.reserved).isTrue()
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
    assertThat(applicationDto.reserved).isTrue()
    assertTelemetryData(applicationDto, eventType = "visit-slot-reserved")
  }

  @Test
  fun `changed booked visit creates new application and updates visit restriction has changed`() {
    // Given
    val reference = bookedVisit.reference
    val newRestriction = if (bookedVisit.visitRestriction == OPEN) CLOSED else OPEN
    val createApplicationRequest = createApplicationRequest(visitRestriction = newRestriction)

    // When
    val responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationRequest, reference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)
    assertThat(applicationDto.reserved).isTrue()
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
    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = visitStart.toLocalDate(), visitStart = visitStart.toLocalTime(), sessionTemplate = sessionTemplate)
    val createApplicationRequest = createApplicationRequest(sessionTemplateReference = sessionTemplate.reference)

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
}
