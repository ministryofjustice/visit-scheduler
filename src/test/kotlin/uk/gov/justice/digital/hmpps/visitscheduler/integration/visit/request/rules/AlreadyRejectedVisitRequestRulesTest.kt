package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.request.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestVisitorDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.OutcomeStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.CANCELLED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestEventAuditRepository
import java.time.LocalTime

class AlreadyRejectedVisitRequestRulesTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  private lateinit var reservedPublicApplication: Application

  private lateinit var visitorDetails: MutableSet<BookingRequestVisitorDetailsDto>

  private val prisonCode = "DFT"

  @Autowired
  private lateinit var testEventAuditRepository: TestEventAuditRepository

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
  fun `when visit was already rejected for same time and same visitor list within rejection rule hours then visit is automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    // request booking is true
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(
        actionedBy = "booking_guy",
        applicationMethodType = ApplicationMethodType.PHONE,
        allowOverBooking = false,
        userType = PUBLIC,
        isRequestBooking = true,
        visitorDetails = visitorDetails,
      ),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // visit is rejected as the number of rejections is equal to the allowed limit
    assertThat(visitDto.visitStatus).isEqualTo(CANCELLED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REJECTED)
    assertThat(visitDto.outcomeStatus).isEqualTo(OutcomeStatus.REQUESTED_VISIT_AUTO_REJECTED)
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list within rejection rule hours but below allowed rejection limit then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 2
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 2)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    // request booking is true
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(
        actionedBy = "booking_guy",
        applicationMethodType = ApplicationMethodType.PHONE,
        allowOverBooking = false,
        userType = PUBLIC,
        isRequestBooking = true,
        visitorDetails = visitorDetails,
      ),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // visit is not rejected as the number of rejections is below the allowed limit
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
    assertThat(visitDto.outcomeStatus).isNull()
  }

  @Test
  fun `when visit was already rejected for same time but different visitor list within rejection rule hours then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for the same session but different visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    // this visitor is different from the already rejected visit
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 324L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    // request booking is true
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(
        actionedBy = "booking_guy",
        applicationMethodType = ApplicationMethodType.PHONE,
        allowOverBooking = false,
        userType = PUBLIC,
        isRequestBooking = true,
        visitorDetails = visitorDetails,
      ),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // visit is not rejected as the visitor list is different
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
    assertThat(visitDto.outcomeStatus).isNull()
  }

  @Test
  fun `when visit was already rejected but different session and same visitor list within rejection rule hours then visit is not automatically rejected`() {
    // Given
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = reservedPublicApplication.prison.code, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0))
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for a different session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplate1, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    // request booking is true
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(
        actionedBy = "booking_guy",
        applicationMethodType = ApplicationMethodType.PHONE,
        allowOverBooking = false,
        userType = PUBLIC,
        isRequestBooking = true,
        visitorDetails = visitorDetails,
      ),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // visit is not rejected as the visitor list is different
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
    assertThat(visitDto.outcomeStatus).isNull()
  }

  @Test
  fun `when visit was already rejected for same time and same visitor list but before rejection rule hours then visit is not automatically rejected`() {
    // Given
    val visitDate = reservedPublicApplication.sessionSlot.slotDate

    // a visit for a different session and same visitor list was already rejected
    val rejectedVisit = visitEntityHelper.create(visitStatus = CANCELLED, visitSubStatus = VisitSubStatus.REJECTED, slotDate = visitDate, sessionTemplate = sessionTemplateDefault, visitContact = ContactDto("Jane Doe", "01111111111", "email@example.com"))
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 321L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 322L, visitContact = false)
    visitEntityHelper.createVisitor(visit = rejectedVisit, nomisPersonId = 323L, visitContact = false)
    eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT)
    val rejectedEventAudit = eventAuditEntityHelper.create(visit = rejectedVisit, type = EventAuditType.REQUESTED_VISIT_REJECTED)
    testEventAuditRepository.updateCreateTimeStamp(rejectedEventAudit.id, rejectedEventAudit.createTimestamp.minusHours(5))
    visitEntityHelper.save(rejectedVisit)

    // total allowed rejection limit is 1
    visitRequestRuleHelper.createAlreadyRejectedRequestRule(prisonCode, rejectionIntervalInHours = 4, totalRejectedVisits = 1)

    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    // request booking is true
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto(
        actionedBy = "booking_guy",
        applicationMethodType = ApplicationMethodType.PHONE,
        allowOverBooking = false,
        userType = PUBLIC,
        isRequestBooking = true,
        visitorDetails = visitorDetails,
      ),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // visit is not rejected as the visitor list is different
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
    assertThat(visitDto.outcomeStatus).isNull()
  }
}
