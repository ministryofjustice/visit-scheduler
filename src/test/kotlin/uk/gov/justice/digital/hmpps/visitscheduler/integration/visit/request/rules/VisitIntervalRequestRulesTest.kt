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

class VisitIntervalRequestRulesTest : IntegrationTestBase() {
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
  fun `when visits already booked are less than allowed limit for prison then visit sub status is set to AUTO_APPROVED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 1 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

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

    // as there is 1 visit before and 1 visit after the visit date and hence less than allowed, the visit should get auto approved
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.AUTO_APPROVED)
  }

  @Test
  fun `when visits already booked before visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 2, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 3, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

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

    // as there are 2 visits before and 1 visit after the visit date and hence more than allowed, the visit should not be auto approved
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
  }

  @Test
  fun `when visits already booked after visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the previous day and 1 on the next day
    createBookedVisits(visitDate.minusDays(1), totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate.plusDays(1), totalVisits = 2, sessionTemplate = sessionTemplate1)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

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

    // as there are 1 visit before and 2 visits after the visit date and hence more than allowed, the visit should not be auto approved
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
  }

  @Test
  fun `when visits already booked for visit date are more than allowed limit for prison then visit sub status is set to REQUESTED`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    val sessionTemplate2 = sessionTemplateEntityHelper.create(prisonCode = prisonCode, startTime = LocalTime.now().plusMinutes(5), endTime = LocalTime.now().plusHours(1))
    // 2 visit exists on the same day already
    createBookedVisits(visitDate, totalVisits = 1, sessionTemplate = sessionTemplate1)
    createBookedVisits(visitDate, totalVisits = 1, sessionTemplate = sessionTemplate2)

    // cancelled visits should not be considered
    createCancelledVisits(visitDate.minusDays(1), totalVisits = 4, sessionTemplate = sessionTemplate1)

    visitRequestRuleHelper.createVisitIntervalRule(prisonCode, allowedVisits = 2, intervalDays = 1)

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

    // as there are 2 visits already booked for the visit date and hence more than allowed, the visit should not be auto approved
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
  }

  private fun createBookedVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(visitDate, totalVisits, sessionTemplate, BOOKED, VisitSubStatus.AUTO_APPROVED)
  }

  private fun createCancelledVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate) {
    createVisits(visitDate, totalVisits, sessionTemplate, VisitStatus.CANCELLED, VisitSubStatus.CANCELLED)
  }

  private fun createVisits(visitDate: LocalDate, totalVisits: Int, sessionTemplate: SessionTemplate, visitStatus: VisitStatus, visitSubStatus: VisitSubStatus) {
    (1..totalVisits).forEach { _ ->
      visitEntityHelper.create(visitStatus = visitStatus, visitSubStatus = visitSubStatus, slotDate = visitDate, sessionTemplate = sessionTemplate, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    }
  }
}
