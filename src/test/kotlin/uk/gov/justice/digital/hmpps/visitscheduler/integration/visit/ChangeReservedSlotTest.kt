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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitReserveSlotChangeUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $APPLICATION_RESERVED_SLOT_CHANGE")
class ChangeReservedSlotTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var applicationMin: Application
  private lateinit var applicationFull: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    sessionTemplate = sessionTemplateEntityHelper.create()

    applicationMin = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplate, reservedSlot = true, completed = false)
    applicationFull = applicationEntityHelper.create(slotDate = startDate, sessionTemplate = sessionTemplate, reservedSlot = true, completed = false)

    applicationEntityHelper.createContact(application = applicationFull, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = applicationFull, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = applicationFull, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(applicationFull)
  }

  @Test
  fun `change application - change all details on original max application`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = newSessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate.plusWeeks(1),
      visitRestriction = swapRestriction(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(applicationFull, applicationDto, updateRequest, newSessionTemplate)

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
      visitRestriction = swapRestriction(applicationFull.restriction),
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = false)),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )

    val applicationReference = applicationMin.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    assertApplicationDetails(applicationMin, applicationDto, updateRequest, newSessionTemplate)

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
      visitRestriction = swapRestriction(applicationMin.restriction),
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
    Assertions.assertThat(applicationDto.visitRestriction).isEqualTo(updateRequest.visitRestriction)
    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(updateRequest.sessionTemplateReference)
    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `change application - start restriction -- start date has not changed`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitRestriction = applicationFull.restriction,
      sessionTemplateReference = sessionTemplate.reference,
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
      visitRestriction = applicationFull.restriction,
      sessionTemplateReference = sessionTemplate.reference,
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
      visitRestriction = swapRestriction(applicationFull.restriction),
      sessionTemplateReference = sessionTemplate.reference,
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
      sessionTemplateReference = sessionTemplate.reference,
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
      sessionTemplateReference = sessionTemplate.reference,
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
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
    )

    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)

    Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(updateRequest.visitorSupport!!.size)
    applicationDto.visitorSupport.forEachIndexed { index, supportDto ->
      Assertions.assertThat(supportDto.type).isEqualTo(updateRequest.visitorSupport!!.toList()[index].type)
      Assertions.assertThat(supportDto.text).isEqualTo(updateRequest.visitorSupport!!.toList()[index].text)
    }

    // And
    assertTelemetry(applicationDto)
  }

  @Test
  fun `when reserved slot changed if session template updated then visit has new session template reference and slot`() {
    // Given
    val newSessionTemplate = sessionTemplateEntityHelper.create()

    val updateRequest = ChangeApplicationDto(
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
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
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      visitRestriction = applicationFull.restriction,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(VisitorDto(123L, visitContact = true), VisitorDto(124L, visitContact = true)),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
  }

  @Test
  fun `when change reserved slot has no visitors then bad request is returned`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      visitRestriction = applicationFull.restriction,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = emptySet(),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
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
      sessionTemplateReference = sessionTemplate.reference,
      sessionDate = applicationFull.sessionSlot.slotDate,
      visitRestriction = applicationFull.restriction,
      visitContact = ContactDto("John Smith", "01234 567890"),
      visitors = setOf(
        VisitorDto(1, true), VisitorDto(2, false),
        VisitorDto(3, true), VisitorDto(4, false),
        VisitorDto(5, false), VisitorDto(6, false),
        VisitorDto(7, false), VisitorDto(8, false),
        VisitorDto(9, false), VisitorDto(10, false),
        VisitorDto(11, false), VisitorDto(12, false),
      ),
      visitorSupport = setOf(VisitorSupportDto("OTHER", "Some Text")),
    )
    val applicationReference = applicationFull.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    responseSpec.expectStatus().isBadRequest
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
    sessionTemplate: SessionTemplate,
  ) {
    Assertions.assertThat(applicationDto.reference).isEqualTo(originalApplication.reference)
    Assertions.assertThat(applicationDto.prisonerId).isEqualTo(originalApplication.prisonerId)
    Assertions.assertThat(applicationDto.prisonCode).isEqualTo(originalApplication.prison.code)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalDate()).isEqualTo(updateRequest.sessionDate)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(applicationDto.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(applicationDto.visitType).isEqualTo(originalApplication.visitType)
    Assertions.assertThat(applicationDto.reserved).isTrue()
    Assertions.assertThat(applicationDto.completed).isFalse()

    Assertions.assertThat(applicationDto.visitRestriction).isEqualTo(updateRequest.visitRestriction)
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

    val supportDtoList = applicationDto.visitorSupport.toList()
    updateRequest.visitorSupport?.let {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(it.size)
      it.forEachIndexed { index, supportDto ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(supportDto.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(supportDto.text)
      }
    } ?: run {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(originalApplication.support.size)
      originalApplication.support.forEachIndexed { index, support ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(support.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(support.text)
      }
    }

    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()
  }

  private fun swapRestriction(restriction: VisitRestriction) = if (OPEN == restriction) CLOSED else OPEN
}
