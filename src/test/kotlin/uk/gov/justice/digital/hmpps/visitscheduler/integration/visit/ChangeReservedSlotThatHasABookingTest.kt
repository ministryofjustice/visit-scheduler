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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitReserveSlotChange
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository

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

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    oldApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplate, completed = true)
    applicationEntityHelper.createContact(application = oldApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = oldApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = oldApplication, name = "OTHER", details = "Some Text")

    applicationEntityHelper.save(oldApplication)

    oldBooking = visitEntityHelper.create(sessionTemplate = sessionTemplate)
    oldBooking.addApplication(oldApplication)

    val newRestriction = if (oldBooking.visitRestriction == OPEN) CLOSED else OPEN

    newApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplate, completed = true, visitRestriction = newRestriction)
    applicationEntityHelper.createContact(application = newApplication, name = "Aled Evans", phone = "01348 811539")
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = newApplication, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(newApplication)

    oldBooking.addApplication(newApplication)
  }

  @Test
  fun `change reserved slot by application reference - start date has changed back to match booked slot`() {
    // Given

    val updateRequest = ChangeApplicationDto(
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldBooking.sessionSlot.slotDate,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = newApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto)!!

    Assertions.assertThat(applicationDto.reserved).isFalse()

    // And
    assertTelemetry(applicationDto, visit)
  }

  @Test
  fun `change reserved slot by application reference - start restriction has changed back to match booked slot`() {
    // Given
    val updateRequest = ChangeApplicationDto(
      visitRestriction = oldBooking.visitRestriction,
      sessionTemplateReference = oldBooking.sessionSlot.sessionTemplateReference!!,
      sessionDate = oldApplication.sessionSlot.slotDate,
      visitContact = ContactDto("John Smith", "01234 567890"),
    )

    val applicationReference = oldApplication.reference

    // When
    val responseSpec = callVisitReserveSlotChange(webTestClient, roleVisitSchedulerHttpHeaders, updateRequest, applicationReference)

    // Then
    val returnResult = getResult(responseSpec)
    val applicationDto = getApplicationDto(returnResult)
    val visit = getVisit(applicationDto)!!

    Assertions.assertThat(applicationDto.reserved).isFalse()
    // And
    assertTelemetry(applicationDto, visit)
  }

  // TODDO create tests similar to ChangeReservedSlot
  // TODO check to see if Application is added to visit and check to see that it is not complete

  private fun assertTelemetry(applicationDto: ApplicationDto, visit: Visit) {
    verify(telemetryClient).trackEvent(
      eq("visit-slot-changed"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["bookingReference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(applicationDto.reference)
        Assertions.assertThat(it["reservedSlot"]).isEqualTo(applicationDto.reserved)
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

  private fun getVisit(dto: ApplicationDto): Visit? {
    return testVisitRepository.findByApplicationReference(dto.reference)
  }
}
