package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_RESERVE_SLOT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlot
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitReserveSlotUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_RESERVE_SLOT")
class ReserveSlotTest : IntegrationTestBase() {

  companion object {
    val visitTime: LocalDateTime = LocalDateTime.of(LocalDate.now().year + 1, 11, 1, 12, 30, 44)
    const val actionedByUserName = "user-1"
  }

  private lateinit var sessionTemplate: SessionTemplate
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
    prisonEntityHelper.create("MDI", true)
  }

  private fun createReserveVisitSlotDto(actionedBy: String = actionedByUserName, sessionTemplate: SessionTemplate): CreateApplicationDto {
    return CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate),
      visitRestriction = OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      actionedBy = actionedBy,
    )
  }

  @Test
  fun `reserve visit slot`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create(startTime = visitTime.toLocalTime(), endTime = visitTime.plusHours(1).toLocalTime())
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then

    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val application = this.getApplication(applicationDto)!!
    assertApplicationDetails(application, applicationDto, reserveVisitSlotDto, sessionTemplate);

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `when reserve visit slot has no visitors then bad request is returned`() {
    // Given
    val createReservationRequest = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate),
      visitRestriction = OPEN,
      visitors = setOf(),
      visitContact = ContactDto("John Smith", "01234 567890"),
      actionedBy = actionedByUserName,
    )

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, createReservationRequest)

    // Then
    responseSpec.expectStatus().isBadRequest

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-bad-request-error"), any(), isNull())
    verify(telemetryClient, times(0)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }

  @Test
  fun `error message when reserve visit has no session template`() {
    // Given

    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)

    // Then
    responseSpec.expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.developerMessage").isEqualTo("Template reference:sessionTemplateReference not found")
  }

  @Test
  fun `when reserve visit slot has more than 10 visitors then bad request is returned`() {
    // Given
    val sessionTemplate = sessionTemplateEntityHelper.create()
    val reserveVisitSlotDto = createReserveVisitSlotDto(sessionTemplate = sessionTemplate)
    reserveVisitSlotDto.visitors = setOf(
      VisitorDto(1, true), VisitorDto(2, false),
      VisitorDto(3, true), VisitorDto(4, false),
      VisitorDto(5, false), VisitorDto(6, false),
      VisitorDto(7, false), VisitorDto(8, false),
      VisitorDto(9, false), VisitorDto(10, false),
      VisitorDto(11, false), VisitorDto(12, false),
    )

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto)
    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `reserve visit slot - only one visit contact allowed`() {
    // Given

    val createReservationRequest = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate),
      visitRestriction = OPEN,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(nomisPersonId = 123, visitContact = true),
        VisitorDto(nomisPersonId = 124, visitContact = true),
      ),
      visitorSupport = setOf(
        VisitorSupportDto("OTHER", "Some Text"),
      ),
      actionedBy = actionedByUserName,
    )

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, createReservationRequest)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `reserve visit slot - invalid support`() {
    // Given
    val createApplicationDto = CreateApplicationDto(
      prisonerId = "FF0000FF",
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = sessionDatesUtil.getFirstBookableSessionDay(sessionTemplate),
      visitRestriction = OPEN,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(),
      visitorSupport = setOf(VisitorSupportDto("ANYTHINGWILLDO")),
      actionedBy = actionedByUserName,
    )

    // When
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders, createApplicationDto)

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
    val responseSpec = callVisitReserveSlot(webTestClient, roleVisitSchedulerHttpHeaders)

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
    val responseSpec = callVisitReserveSlot(webTestClient, authHttpHeaders, reserveVisitSlotDto)

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
    val responseSpec = webTestClient.post().uri(getVisitReserveSlotUrl())
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
    application: Application,
    applicationDto: ApplicationDto,
    createApplicationRequest: CreateApplicationDto,
    sessionTemplate: SessionTemplate,
  ) {
    Assertions.assertThat(applicationDto.reference).isEqualTo(application.reference)
    Assertions.assertThat(applicationDto.prisonerId).isEqualTo(application.prisonerId)
    Assertions.assertThat(applicationDto.prisonCode).isEqualTo(application.prison.code)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalDate()).isEqualTo(createApplicationRequest.sessionDate)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(applicationDto.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(applicationDto.visitType).isEqualTo(application.visitType)
    Assertions.assertThat(applicationDto.reserved).isTrue()
    Assertions.assertThat(applicationDto.completed).isFalse()
    Assertions.assertThat(applicationDto.visitRestriction).isEqualTo(createApplicationRequest.visitRestriction)
    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(createApplicationRequest.sessionTemplateReference)

    createApplicationRequest.visitContact?.let {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(it.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(it.telephone)
    } ?: run {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(application.visitContact?.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(application.visitContact?.telephone)
    }

    val visitorsDtoList = applicationDto.visitors.toList()
    createApplicationRequest.visitors.let {
      Assertions.assertThat(applicationDto.visitors.size).isEqualTo(it.size)
      it.forEachIndexed { index, visitorDto ->
        Assertions.assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitorDto.nomisPersonId)
        Assertions.assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitorDto.visitContact)
      }
    }

    val supportDtoList = applicationDto.visitorSupport.toList()
    createApplicationRequest.visitorSupport?.let {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(it.size)
      it.forEachIndexed { index, supportDto ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(supportDto.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(supportDto.text)
      }
    } ?: run {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(application.support.size)
      application.support.forEachIndexed { index, support ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(support.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(support.text)
      }
    }

    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()
  }

  private fun assertTelemetry(applicationDto: ApplicationDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-slot-reserved"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["applicationReference"]).isEqualTo(applicationDto.reference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(applicationDto.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(applicationDto.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(applicationDto.visitType.name)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(applicationDto.visitRestriction.name)
        Assertions.assertThat(it["visitStart"])
          .isEqualTo(applicationDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["reserved"]).isEqualTo(applicationDto.reserved)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-reserved"), any(), isNull())
  }
}
