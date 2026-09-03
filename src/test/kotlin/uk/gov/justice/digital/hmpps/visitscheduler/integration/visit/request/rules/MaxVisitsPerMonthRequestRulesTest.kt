package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.request.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestVisitorDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

class MaxVisitsPerMonthRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private lateinit var visitorDetails: MutableSet<BookingRequestVisitorDetailsDto>

  private val prisonCode = "DFT"

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)
    applicationEntityHelper.createContact(application = reservedPublicApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 322L, visitContact = false)
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 323L, visitContact = false)
    applicationEntityHelper.createSupport(application = reservedPublicApplication, description = "Some Text")
    reservedPublicApplication = applicationEntityHelper.save(reservedPublicApplication)

    visitorDetails = mutableSetOf()
    visitorDetails.add(BookingRequestVisitorDetailsDto(321L, 21))
    visitorDetails.add(BookingRequestVisitorDetailsDto(322L, 25))
    visitorDetails.add(BookingRequestVisitorDetailsDto(323L, null))
  }

  @Test
  fun `when visits already booked are less than max visits allowed for prison then visit sub status is set to AUTO_APPROVED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month, 2 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 2, sessionTemplate = sessionTemplate1)

    // cancelled visits for the month should not be considered
    createCancelledVisits(visitDate, totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(actionedBy = "booking_guy", applicationMethodType = ApplicationMethodType.PHONE, allowOverBooking = false, userType = PUBLIC, isRequestBooking = false, visitorDetails = visitorDetails),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // as there are only 2 visits booked for the month and max visits allowed is 3, the visit should get auto approved
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.AUTO_APPROVED)
  }

  @Test
  fun `when visits already booked are same as max visits allowed for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month and 3 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 3, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(actionedBy = "booking_guy", applicationMethodType = ApplicationMethodType.PHONE, allowOverBooking = false, userType = PUBLIC, isRequestBooking = false, visitorDetails = visitorDetails),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // as there are already 3 visits booked for the month and max visits allowed is 3, the visit should not get auto approved and instead move into REQUESTED
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
  }

  @Test
  fun `when visits already booked are more than max visits allowed for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // create 3 visits for last month, 3 visits for next month and 3 visits for current month on a different session template
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    createBookedVisits(visitDate.minusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusMonths(1), totalVisits = 3, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 5, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createMaxVisitsPerMonthRule(prisonCode, 3)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(actionedBy = "booking_guy", applicationMethodType = ApplicationMethodType.PHONE, allowOverBooking = false, userType = PUBLIC, isRequestBooking = false, visitorDetails = visitorDetails),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // as there are already 3 visits booked for the month and max visits allowed is 3, the visit should not get auto approved and instead move into REQUESTED
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
  }

  private fun createBookedVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(firstDatOfMonth, totalVisits, sessionTemplate, BOOKED, VisitSubStatus.AUTO_APPROVED)
  }

  private fun createCancelledVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(firstDatOfMonth, totalVisits, sessionTemplate, VisitStatus.CANCELLED, VisitSubStatus.CANCELLED)
  }

  private fun createVisits(firstDatOfMonth: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate, visitStatus: VisitStatus, visitSubStatus: VisitSubStatus) {
    for (dayOfMonth in 1..totalVisits) {
      val visitDate = LocalDate.of(firstDatOfMonth.year, firstDatOfMonth.month, dayOfMonth)
      visitEntityHelper.create(visitStatus = visitStatus, visitSubStatus = visitSubStatus, slotDate = visitDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    }
  }
}
