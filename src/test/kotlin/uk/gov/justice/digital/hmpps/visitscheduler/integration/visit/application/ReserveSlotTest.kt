package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getSubmitApplicationUrl
import uk.gov.justice.digital.hmpps.visitscheduler.helper.submitApplication
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $APPLICATION_RESERVE_SLOT")
class ReserveSlotTest : IntegrationTestBase() {

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)
    const val ACTIONED_BY_USER_NAME = "user-1"
  }

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var testSessionTemplateRepository: TestSessionTemplateRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonEntityHelper.create("MDI", true)
  }

  private fun createReserveVisitSlotDto(
    actionedBy: String = ACTIONED_BY_USER_NAME,
    prisonerId: String = "FF0000FF",
    sessionTemplate: SessionTemplate? = null,
    slotDate: LocalDate? = null,
    support: String = "Some Text",
    sessionRestriction: SessionRestriction = OPEN,
    allowOverBooking: Boolean = false,
  ): CreateApplicationDto {
    return CreateApplicationDto(
      prisonerId,
      sessionTemplateReference = sessionTemplate?.reference ?: "IDontExistSessionTemplate",
      sessionDate = slotDate ?: sessionTemplate?.let { sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate) } ?: LocalDate.now(),
      applicationRestriction = sessionRestriction,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto(support),
      actionedBy = actionedBy,
      userType = STAFF,
      allowOverBooking = allowOverBooking,
    )
  }

  @Test
  fun `reserve visit slot`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(startTime = visitTime.toLocalTime(), endTime = visitTime.plusHours(1).toLocalTime())
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then

    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val application = this.getApplication(applicationDto)!!
    assertApplicationDetails(application, applicationDto, reserveVisitSlotDto)

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `reserve visit slot capacity - over capacity exception`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN
    val sessionRestriction = SessionRestriction.get(visitRestriction)

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val existingBooking = createApplicationAndVisit(sessionTemplate = sessionTemplate, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate, sessionRestriction = sessionRestriction, slotDate = existingBooking.sessionSlot.slotDate)

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    assertHelper.assertCapacityError(responseSpec)
  }

  @Test
  fun `reserve visit slot capacity - no capacity exception thrown as we have allowed over booking`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN
    val sessionRestriction = SessionRestriction.get(visitRestriction)

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val existingBooking = createApplicationAndVisit(sessionTemplate = sessionTemplate, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(allowOverBooking = true, sessionTemplate = sessionTemplate, sessionRestriction = sessionRestriction, slotDate = existingBooking.sessionSlot.slotDate)

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isCreated
  }

  @Test
  fun `reserve visit slot capacity gone due to reserved application - over capacity exception`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN
    val sessionRestriction = SessionRestriction.get(visitRestriction)

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val application = createApplicationAndSave(sessionTemplate = sessionTemplate, completed = false, reservedSlot = true, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(
      prisonerId = application.prisonerId,
      sessionTemplate = sessionTemplate,
      sessionRestriction = sessionRestriction,
      slotDate = application.sessionSlot.slotDate,
    )

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    assertHelper.assertCapacityError(responseSpec)
  }

  @Test
  fun `reserve visit slot capacity - change restriction`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val existingBooking = createApplicationAndVisit(sessionTemplate = sessionTemplate, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate, sessionRestriction = SessionRestriction.CLOSED, slotDate = existingBooking.sessionSlot.slotDate)

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isCreated
  }

  @Test
  fun `reserve visit slot capacity - change restriction but no capacity on new restriction`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 0)
    val existingBooking = createApplicationAndVisit(sessionTemplate = sessionTemplate, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate, sessionRestriction = SessionRestriction.CLOSED, slotDate = existingBooking.sessionSlot.slotDate)

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    assertHelper.assertCapacityError(responseSpec)
  }

  @Test
  fun `reserve visit slot capacity - change slot date`() {
    // Given
    val visitRestriction = VisitRestriction.OPEN

    val sessionTemplate = sessionTemplateEntityHelper.create(openCapacity = 1, closedCapacity = 1)
    val existingBooking = createApplicationAndVisit(sessionTemplate = sessionTemplate, visitRestriction = visitRestriction)
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate, sessionRestriction = SessionRestriction.CLOSED, slotDate = existingBooking.sessionSlot.slotDate.plusWeeks(1))

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isCreated
  }

  @Test
  fun `reserve visit slot and support is less than three then exception thrown`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(startTime = visitTime.toLocalTime(), endTime = visitTime.plusHours(1).toLocalTime())
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate, support = "12")

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Support value description is too small")
  }

  @Test
  fun `when reserve visit slot has no visitors then bad request is returned`() {
    // Given
    val createReservationRequest = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault),
      applicationRestriction = OPEN,
      visitors = setOf(),
      visitContact = ContactDto("John Smith", "01234 567890"),
      actionedBy = ACTIONED_BY_USER_NAME,
      userType = STAFF,
    )

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, createReservationRequest)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `error message when reserve visit has no session template`() {
    // Given

    val reserveVisitSlotDto = createReserveVisitSlotDto()

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:IDontExistSessionTemplate not found")
  }

  @Test
  fun `when reserve visit slot has more than max visitors then bad request is returned`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)
    reserveVisitSlotDto.visitors = setOf(
      VisitorDto(1, true), VisitorDto(2, false),
      VisitorDto(3, false), VisitorDto(4, false),
      VisitorDto(5, false), VisitorDto(6, false),
      VisitorDto(7, false),
    )

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)
    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("This application has too many Visitors for this prison MDI max visitors 6")
  }

  @Test
  fun `reserve visit slot - only one visit contact allowed`() {
    // Given

    val createReservationRequest = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault),
      applicationRestriction = OPEN,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(nomisPersonId = 123, visitContact = true),
        VisitorDto(nomisPersonId = 124, visitContact = true),
      ),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = ACTIONED_BY_USER_NAME,
      userType = STAFF,
    )

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, createReservationRequest)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage")
      .value(Matchers.containsString("Only one visit contact allowed"))
  }

  @Test
  fun `reserve visit slot - invalid support`() {
    // Given
    val createApplicationDto = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplateDefault),
      applicationRestriction = OPEN,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(),
      visitorSupport = ApplicationSupportDto("ANYTHINGWILLDO"),
      actionedBy = ACTIONED_BY_USER_NAME,
      userType = STAFF,
    )

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationDto)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `reserve visit slot - invalid request`() {
    // Given

    // When
    val responseSpec = submitApplication(webTestClient, roleVisitSchedulerHttpHeaders)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `reserve visit slot - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)

    // When
    val responseSpec = submitApplication(webTestClient, authHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `reserve visit slot - unauthorised when no token`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)

    val jsonBody = BodyInserters.fromValue(reserveVisitSlotDto)

    // When
    val responseSpec = webTestClient.post().uri(getSubmitApplicationUrl())
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun getApplicationDto(returnResult: EntityExchangeResult<ByteArray>): ApplicationDto {
    return objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)
  }

  private fun getResult(responseSpec: ResponseSpec): EntityExchangeResult<ByteArray> {
    return responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()
  }

  private fun getApplication(dto: ApplicationDto): Application? {
    return testApplicationRepository.findByReference(dto.reference)
  }

  private fun assertApplicationDetails(
    persistedApplication: Application,
    returnedApplication: ApplicationDto,
    createApplicationRequest: CreateApplicationDto,
  ) {
    val sessionTemplate = testSessionTemplateRepository.findByReference(createApplicationRequest.sessionTemplateReference)

    assertThat(returnedApplication.reference).isEqualTo(persistedApplication.reference)
    assertThat(returnedApplication.prisonerId).isEqualTo(persistedApplication.prisonerId)
    assertThat(returnedApplication.prisonCode).isEqualTo(sessionTemplate.prison.code)
    assertThat(returnedApplication.startTimestamp.toLocalDate()).isEqualTo(createApplicationRequest.sessionDate)
    assertThat(returnedApplication.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    assertThat(returnedApplication.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    assertThat(returnedApplication.visitType).isEqualTo(persistedApplication.visitType)
    assertThat(returnedApplication.reserved).isTrue()
    assertThat(returnedApplication.completed).isFalse()
    assertThat(returnedApplication.visitRestriction.name).isEqualTo(createApplicationRequest.applicationRestriction.name)
    assertThat(returnedApplication.sessionTemplateReference).isEqualTo(createApplicationRequest.sessionTemplateReference)
    assertThat(returnedApplication.userType).isEqualTo(createApplicationRequest.userType)

    createApplicationRequest.visitContact?.let {
      assertThat(returnedApplication.visitContact!!.name).isEqualTo(it.name)
      assertThat(returnedApplication.visitContact!!.telephone).isEqualTo(it.telephone)
    } ?: run {
      assertThat(returnedApplication.visitContact!!.name).isEqualTo(persistedApplication.visitContact?.name)
      assertThat(returnedApplication.visitContact!!.telephone).isEqualTo(persistedApplication.visitContact?.telephone)
    }

    val visitorsDtoList = returnedApplication.visitors.toList()
    createApplicationRequest.visitors.let {
      assertThat(returnedApplication.visitors.size).isEqualTo(it.size)
      it.forEachIndexed { index, visitorDto ->
        assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitorDto.nomisPersonId)
        assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitorDto.visitContact)
      }
    }

    createApplicationRequest.visitorSupport?.let {
      assertThat(it.description).isEqualTo(returnedApplication.visitorSupport?.description)
    } ?: run {
      assertThat(persistedApplication.support).isEqualTo(returnedApplication.visitorSupport?.description)
    }

    assertThat(returnedApplication.createdTimestamp).isNotNull()
  }

  private fun assertTelemetry(applicationDto: ApplicationDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-slot-reserved"),
      org.mockito.kotlin.check {
        assertThat(it["applicationReference"]).isEqualTo(applicationDto.reference)
        assertThat(it["prisonerId"]).isEqualTo(applicationDto.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(applicationDto.prisonCode)
        assertThat(it["visitType"]).isEqualTo(applicationDto.visitType.name)
        assertThat(it["visitRestriction"]).isEqualTo(applicationDto.visitRestriction.name)
        assertThat(it["visitStart"])
          .isEqualTo(applicationDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["reserved"]).isEqualTo(applicationDto.reserved.toString())
        assertThat(it["userType"]).isEqualTo(applicationDto.userType.toString())
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }
}
