package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.application

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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestSessionTemplateRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $APPLICATION_RESERVED_SLOT_CHANGE")
class ChangeReservedSlotThatHasABookingTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @Autowired
  private lateinit var testSessionTemplateRepository: TestSessionTemplateRepository

  private lateinit var oldBooking: Visit
  private lateinit var oldApplication: Application

  private lateinit var initialChangeApplication: Application
  private lateinit var newRestriction: VisitRestriction

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    oldBooking = createApplicationAndVisit("test", sessionTemplateDefault, BOOKED, LocalDate.now())
    oldApplication = oldBooking.getLastApplication()!!

    newRestriction = if (oldBooking.visitRestriction == OPEN) CLOSED else OPEN
    initialChangeApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false, reservedSlot = true, visitRestriction = newRestriction)
    applicationEntityHelper.createContact(application = initialChangeApplication, name = "Aled Wyn Evans", phone = "01348 811539")
    applicationEntityHelper.createVisitor(application = initialChangeApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = initialChangeApplication, name = "OTHER", details = "Some Text")
    initialChangeApplication = applicationEntityHelper.save(initialChangeApplication)

    oldBooking.addApplication(initialChangeApplication)
    visitEntityHelper.save(oldBooking)
  }

  @Test
  fun `change reserved slot by application reference - visit restriction has now changed back to original booking and application is not Reserved`() {
    // Given

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldBooking.sessionSlot.slotDate,
      applicationRestriction = CreateApplicationRestriction.get(oldBooking.visitRestriction),
    )

    val applicationReference = initialChangeApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)

    Assertions.assertThat(visit).isNotNull
    assertChangedApplicationDetails(initialChangeApplication, applicationDto, updateRequest, false)

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - visit restriction has still changed from original booking and application is Reserved`() {
    // Given

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldBooking.sessionSlot.slotDate,
      applicationRestriction = CreateApplicationRestriction.get(initialChangeApplication.restriction),
    )

    val applicationReference = initialChangeApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)

    Assertions.assertThat(visit).isNotNull
    assertChangedApplicationDetails(initialChangeApplication, applicationDto, updateRequest, true)

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - session slot has now change back to original booking and application is not Reserved`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldApplication.sessionSlot.slotDate,
      applicationRestriction = CreateApplicationRestriction.get(oldBooking.visitRestriction),
    )

    val applicationReference = initialChangeApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)
    Assertions.assertThat(visit).isNotNull

    assertChangedApplicationDetails(initialChangeApplication, applicationDto, updateRequest, false)

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - session slot has still changed from original booking and application is Reserved`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = initialChangeApplication.sessionSlot.sessionTemplateReference!!,
      sessionDate = initialChangeApplication.sessionSlot.slotDate,
    )

    val applicationReference = initialChangeApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)
    Assertions.assertThat(visit).isNotNull

    assertChangedApplicationDetails(initialChangeApplication, applicationDto, updateRequest, true)

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  private fun assertChangedApplicationDetails(
    initialApplication: Application,
    applicationDto: ApplicationDto,
    changeApplicationRequest: ChangeApplicationDto,
    reserved: Boolean,
  ) {
    val sessionTemplate = testSessionTemplateRepository.findByReference(changeApplicationRequest.sessionTemplateReference)

    Assertions.assertThat(applicationDto.reference).isEqualTo(initialApplication.reference)
    Assertions.assertThat(applicationDto.prisonerId).isEqualTo(initialApplication.prisonerId)
    Assertions.assertThat(applicationDto.prisonCode).isEqualTo(sessionTemplate.prison.code)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalTime()).isEqualTo(sessionTemplate.startTime)
    Assertions.assertThat(applicationDto.endTimestamp.toLocalTime()).isEqualTo(sessionTemplate.endTime)
    Assertions.assertThat(applicationDto.startTimestamp.toLocalDate()).isEqualTo(changeApplicationRequest.sessionDate)

    Assertions.assertThat(applicationDto.visitType).isEqualTo(initialApplication.visitType)
    Assertions.assertThat(applicationDto.reserved).isEqualTo(reserved)
    Assertions.assertThat(applicationDto.completed).isFalse()
    changeApplicationRequest.applicationRestriction?.let {
      Assertions.assertThat(applicationDto.visitRestriction.name).isEqualTo(it.name)
    } ?: run {
      Assertions.assertThat(applicationDto.visitRestriction.name).isEqualTo(initialApplication.restriction.name)
    }

    Assertions.assertThat(applicationDto.sessionTemplateReference).isEqualTo(changeApplicationRequest.sessionTemplateReference)

    changeApplicationRequest.visitContact?.let {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(it.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(it.telephone)
    } ?: run {
      Assertions.assertThat(applicationDto.visitContact!!.name).isEqualTo(initialApplication.visitContact?.name)
      Assertions.assertThat(applicationDto.visitContact!!.telephone).isEqualTo(initialApplication.visitContact?.telephone)
    }

    val visitorsDtoList = applicationDto.visitors.toList()
    changeApplicationRequest.visitors?.let {
      Assertions.assertThat(applicationDto.visitors.size).isEqualTo(it.size)
      it.forEachIndexed { index, visitorDto ->
        Assertions.assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitorDto.nomisPersonId)
        Assertions.assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitorDto.visitContact)
      }
    } ?: run {
      Assertions.assertThat(applicationDto.visitors.size).isEqualTo(initialApplication.visitors.size)
      initialApplication.visitors.forEachIndexed { index, visitor ->
        Assertions.assertThat(visitorsDtoList[index].nomisPersonId).isEqualTo(visitor.nomisPersonId)
        Assertions.assertThat(visitorsDtoList[index].visitContact).isEqualTo(visitor.contact)
      }
    }

    val supportDtoList = applicationDto.visitorSupport.toList()
    changeApplicationRequest.visitorSupport?.let {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(it.size)
      it.forEachIndexed { index, supportDto ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(supportDto.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(supportDto.text)
      }
    } ?: run {
      Assertions.assertThat(applicationDto.visitorSupport.size).isEqualTo(initialApplication.support.size)
      initialApplication.support.forEachIndexed { index, support ->
        Assertions.assertThat(supportDtoList[index].type).isEqualTo(support.type)
        Assertions.assertThat(supportDtoList[index].text).isEqualTo(support.text)
      }
    }

    Assertions.assertThat(applicationDto.createdTimestamp).isNotNull()
  }

  private fun assertTelemetry(applicationDto: ApplicationDto, visit: Visit) {
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["bookingReference"]).isEqualTo(visit.reference)
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

  private fun getVisit(bookingReference: String): Visit? {
    return testVisitRepository.findByApplicationReference(bookingReference)
  }
}
