package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitReserveSlotChangeUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $APPLICATION_RESERVED_SLOT_CHANGE")
class ChangeReservedSlotTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @Autowired
  private lateinit var testSessionTemplateRepository: TestSessionTemplateRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var applicationMin: Application
  private lateinit var applicationFull: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    sessionTemplateDefault = sessionTemplateEntityHelper.create()

    applicationMin = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplateDefault, reservedSlot = true, completed = false)
    applicationFull = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplateDefault, reservedSlot = true, completed = false)

    applicationEntityHelper.createContact(application = applicationFull, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = applicationFull, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = applicationFull, description = "Some Text")
    applicationEntityHelper.save(applicationFull)
  }

  @Test
  fun `change application - change all details on original max application`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = newSessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate.plusWeeks(1),
      applicationRestriction = swapRestriction(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(applicationFull, applicationDto, updateRequest)

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change application - change all details on original min application`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = newSessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate.plusWeeks(1),
      applicationRestriction = swapRestriction(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
    )

    val applicationReference = applicationMin.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(applicationMin, applicationDto, updateRequest)

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change application - add min details`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = newSessionTemplate.reference,
      sessionDate = applicationMin.sessionSlot.slotDate,
      applicationRestriction = swapRestriction(applicationMin.restriction),
    )

    val applicationReference = applicationMin.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)

    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.reference).isEqualTo(applicationMin.reference)
    Assertions.assertThat(applicationDto.prisonerId).isEqualTo(applicationMin.prisonerId)
    Assertions.assertThat(applicationDto.prisonCode).isEqualTo(applicationMin.prison.code)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalDate()).isEqualTo(updateRequest.sessionDate)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalTime()).isEqualTo(newSessionTemplate.startTime)
    Assertions.assertThat(applicationDto.endTimestamp.toLocalTime()).isEqualTo(newSessionTemplate.endTime)
    Assertions.assertThat(applicationDto.visitType).isEqualTo(applicationMin.visitType)
    Assertions.assertThat(applicationDto.reserved).isTrue()
    Assertions.assertThat(applicationDto.visitRestriction.name).isEqualTo(updateRequest.applicationRestriction?.name)
    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(updateRequest.sessionTemplateReference)
    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change application - start restriction -- start date has not changed`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      applicationRestriction = SessionRestriction.get(applicationFull.restriction),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.reserved).isTrue()

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot by application reference - slot date has changed`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      applicationRestriction = SessionRestriction.get(applicationFull.restriction),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate.plusWeeks(1),
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.reserved).isTrue()

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot by application reference - start restriction has changed`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      applicationRestriction = swapRestriction(applicationFull.restriction),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.reserved).isTrue()

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot - contact`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitContact = ContactDto("John Smith", "01234 567890"),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(updateRequest.visitContact!!.name)
    Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(updateRequest.visitContact!!.telephone)

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot - amend visitors`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitors = setOf(VisitorDto(123L, visitContact = true)),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.visitors.size).isEqualTo(updateRequest.visitors!!.size)
    applicationDto.visitors.forEachIndexed { index, visitorDto ->
      Assertions.assertThat(visitorDto.nomisPersonId).isEqualTo(updateRequest.visitors!!.toList()[index].nomisPersonId)
      Assertions.assertThat(visitorDto.visitContact).isEqualTo(updateRequest.visitors!!.toList()[index].visitContact)
    }

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot - amend support`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitorSupport = ApplicationSupportDto("Some Other Text"),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    applicationDto.visitorSupport?.let {
      Assertions.assertThat(it.description).isEqualTo(updateRequest.visitorSupport?.description)
    }

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot - delete support when empty description given`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitorSupport = ApplicationSupportDto(""),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    val application = this.testApplicationRepository.findByReference(applicationDto.reference)

    Assertions.assertThat(application?.support).isNull()
  }

  @Test
  fun `change reserved slot - delete support when blank description given`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitorSupport = ApplicationSupportDto("    "),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    val application = this.testApplicationRepository.findByReference(applicationDto.reference)

    Assertions.assertThat(application?.support).isNull()
  }

  @Test
  fun `change reserved slot - no change when  visitorSupport is null`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitorSupport = null,
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    val application = this.testApplicationRepository.findByReference(applicationDto.reference)

    Assertions.assertThat(application?.support!!.description).isEqualTo(applicationFull.support!!.description)
  }

  @Test
  fun `change reserved slot and support is less than three then exception thrown`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitorSupport = ApplicationSupportDto("12"),
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("Support value description is too small")
  }

  @Test
  fun `when reserved slot changed if session template updated then visit has new session template reference and slot`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      visitorSupport = ApplicationSupportDto("Some Text"),
      sessionTemplateReference = newSessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val application = getApplication(applicationDto)!!

    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(newSessionTemplate.reference)
    Assertions.assertThat(application.sessionSlot.id).isNotEqualTo(applicationFull.sessionSlot.id)

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change reserved slot by application reference - only one visit contact allowed`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      applicationRestriction = SessionRestriction.get(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = true)),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.developerMessage")
      .value(Matchers.containsString("Only one visit contact allowed"))
  }

  @Test
  fun `when change reserved slot has no visitors then bad request is returned`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      applicationRestriction = SessionRestriction.get(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = emptySet(),
      visitorSupport = ApplicationSupportDto("Some Text"),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when change reserved slot has more than 10 visitors then bad request is returned`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = sessionTemplateDefault.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      applicationRestriction = SessionRestriction.get(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(1, true),
        VisitorDto(2, false),
        VisitorDto(3, false),
        VisitorDto(4, false),
        VisitorDto(5, false),
        VisitorDto(6, false),
        VisitorDto(7, false),
      ),
      visitorSupport = ApplicationSupportDto("Some Text"),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.validationMessages[0]").isEqualTo("This application has too many Visitors for this prison MDI max visitors 6")
  }

  @Test
  fun `change reserved slot - not found`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = "aa-bb-cc-dd",
      sessionDate = LocalDate.now(),
    )

    val applicationReference = "IM NOT HERE"

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isNotFound
  }

  @Test
  fun `change reserved slot - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = "aa-bb-cc-dd",
      sessionDate = LocalDate.now(),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, authHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `change reserved slot - unauthorised when no token`() {
    // Given
    val jsonBody = BodyInserters.fromValue(
      ChangeApplicationDto(
        sessionTemplateReference = "aa-bb-cc-dd",
        sessionDate = LocalDate.now(),
      ),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = webTestClient.post().uri(getVisitReserveSlotChangeUrl(applicationReference))
      .body(jsonBody)
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  private fun assertTelemetry(applicationDto: ApplicationDto) {
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["applicationReference"]).isEqualTo(applicationDto.reference)
        Assertions.assertThat(it["reservedSlot"]).isEqualTo(applicationDto.reserved.toString())
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-slot-changed"), any(), isNull())
  }

  private fun getResult(responseSpec: ResponseSpec): EntityExchangeResult<ByteArray> {
    return responseSpec.expectStatus().isOk
      .expectBody()
      .returnResult()
  }

  private fun getApplicationDto(returnResult: EntityExchangeResult<ByteArray>): ApplicationDto {
    return objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)
  }

  private fun getApplication(dto: ApplicationDto): Application? {
    return testApplicationRepository.findByReference(dto.reference)
  }

  private fun assertApplicationDetails(
    originalApplication: Application,
    applicationDto: ApplicationDto,
    updateRequest: ChangeApplicationDto,
  ) {
    val sessionTemplate = testSessionTemplateRepository.findByReference(updateRequest.sessionTemplateReference)

    Assertions.assertThat(applicationDto.reference).isEqualTo(originalApplication.reference)
    Assertions.assertThat(applicationDto.prisonerId).isEqualTo(originalApplication.prisonerId)
    Assertions.assertThat(applicationDto.prisonCode).isEqualTo(sessionTemplate.prison.code)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalDate()).isEqualTo(updateRequest.sessionDate)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(applicationDto.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(applicationDto.visitType).isEqualTo(originalApplication.visitType)
    Assertions.assertThat(applicationDto.reserved).isTrue()
    Assertions.assertThat(applicationDto.completed).isFalse()

    Assertions.assertThat(applicationDto.visitRestriction.name).isEqualTo(updateRequest.applicationRestriction?.name)
    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(updateRequest.sessionTemplateReference)

    updateRequest.visitContact?.let {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(it.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(it.telephone)
    } ?: run {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(originalApplication.visitContact?.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(originalApplication.visitContact?.telephone)
    }

    val visitorsDtoList = applicationDto.visitors.toList()
    updateRequest.visitors?.let {
      Assertions.assertThat(applicationDto.visitors.size).isEqualTo(it.size)
      it.forEachIndexed { index, visitorDto ->
        Assertions.assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitorDto.nomisPersonId)
        Assertions.assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitorDto.visitContact)
      }
    } ?: run {
      Assertions.assertThat(applicationDto.visitors.size).isEqualTo(originalApplication.visitors.size)
      originalApplication.visitors.forEachIndexed { index, visitor ->
        Assertions.assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitor.nomisPersonId)
        Assertions.assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitor.contact)
      }
    }

    updateRequest.visitorSupport?.let {
      Assertions.assertThat(applicationDto.visitorSupport?.description).isEqualTo(it.description)
    } ?: run {
      Assertions.assertThat(applicationDto.visitorSupport?.description).isEqualTo(originalApplication.support)
    }

    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()
  }

  private fun swapRestriction(restriction: VisitRestriction) = if (OPEN == restriction) SessionRestriction.CLOSED else SessionRestriction.OPEN
}
