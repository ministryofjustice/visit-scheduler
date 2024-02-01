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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.APPLICATION_RESERVED_SLOT_CHANGE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ChangeApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
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

  private lateinit var oldBooking: Visit
  private lateinit var oldApplication: Application

  private lateinit var newApplication: Application
  private lateinit var newRestriction: VisitRestriction

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    oldBooking = createApplicationAndVisit("test", sessionTemplateDefault, BOOKED, LocalDate.now())
    oldApplication = oldBooking.getLastApplication()!!

    newRestriction = if (oldBooking.visitRestriction == OPEN) CLOSED else OPEN
    newApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false, reservedSlot = true, visitRestriction = newRestriction)
    applicationEntityHelper.createContact(application = newApplication, name = "Aled Wyn Evans", phone = "01348 811539")
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = newApplication, name = "OTHER", details = "Some Text")
    newApplication = applicationEntityHelper.save(newApplication)

    oldBooking.addApplication(newApplication)
    visitEntityHelper.save(oldBooking)
  }

  @Test
  fun `change reserved slot by application reference - visit restriction has now changed back to original booking and application is not Reserved`() {
    // Given

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldBooking.sessionSlot.slotDate,
      visitRestriction = oldBooking.visitRestriction,
    )

    val applicationReference = newApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)

    Assertions.assertThat(visit).isNotNull
    Assertions.assertThat(applicationDto.reserved).isFalse()

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - visit restriction has still changed from original booking and application is Reserved`() {
    // Given

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldBooking.sessionSlot.slotDate,
      visitRestriction = newApplication.restriction,
    )

    val applicationReference = newApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)

    Assertions.assertThat(visit).isNotNull
    Assertions.assertThat(applicationDto.reserved).isTrue()

    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - session slot has now change back to original booking and application is not Reserved`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldApplication.sessionSlot.slotDate,
      visitRestriction = oldBooking.visitRestriction,
    )

    val applicationReference = newApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)
    Assertions.assertThat(visit).isNotNull

    Assertions.assertThat(applicationDto.reserved).isFalse()
    // And
    assertTelemetry(applicationDto, visit!!)
  }

  @Test
  fun `change reserved slot by application reference - session slot has still changed from original booking and application is Reserved`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = newApplication.sessionSlot.sessionTemplateReference!!,
      sessionDate = newApplication.sessionSlot.slotDate,
    )

    val applicationReference = newApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto.reference)
    Assertions.assertThat(visit).isNotNull

    Assertions.assertThat(applicationDto.reserved).isTrue()
    // And
    assertTelemetry(applicationDto, visit!!)
  }

  // TODDO create tests similar to ChangeReservedSlot
  // TODO check to see if Application is added to visit and check to see that it is not complete

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
