package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestVisitorDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.TelemetryClientService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class BookVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedStaffApplication: Application

  private lateinit var reservedPublicApplication: Application

  private lateinit var visitorDetails: MutableSet<BookingRequestVisitorDetailsDto>

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedStaffApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS)
    applicationEntityHelper.createContact(application = reservedStaffApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedStaffApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createVisitor(application = reservedStaffApplication, nomisPersonId = 322L, visitContact = false)
    applicationEntityHelper.createVisitor(application = reservedStaffApplication, nomisPersonId = 323L, visitContact = false)
    applicationEntityHelper.createSupport(application = reservedStaffApplication, description = "Some Text")
    reservedStaffApplication = applicationEntityHelper.save(reservedStaffApplication)

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
  fun `Book a requested visit (booked via public application)`() {
    // Given
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
      bookingRequestDto = BookingRequestDto("booking_guy", ApplicationMethodType.PHONE, false, PUBLIC, true, visitorDetails = visitorDetails),
    )

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    assertVisitMatchesApplication(visitDto, reservedPublicApplication)
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity, PUBLIC, isRequestVisit = true)
    assertThat(visitEntity.getApplications().size).isEqualTo(1)
    assertThat(visitEntity.getLastApplication()?.reference).isEqualTo(applicationReference)
    assertThat(visitEntity.getLastApplication()?.applicationStatus).isEqualTo(ACCEPTED)
    assertThat(visitEntity.visitSubStatus).isEqualTo(VisitSubStatus.REQUESTED)
    assertBookedEvent(visitDto, isRequestVisit = true)
  }

  @Test
  fun `Book visit by application Reference`() {
    // Given
    val applicationReference = reservedStaffApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    assertVisitMatchesApplication(visitDto, reservedStaffApplication)
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity)
    assertThat(visitEntity.getApplications().size).isEqualTo(1)
    assertThat(visitEntity.getLastApplication()?.reference).isEqualTo(applicationReference)
    assertThat(visitEntity.getLastApplication()?.applicationStatus).isEqualTo(ACCEPTED)

    // And
    assertBookedEvent(visitDto)
  }

  @Test
  fun `Book visit by public application`() {
    // Given
    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, "john", "smith", prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, userType = PUBLIC, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    assertVisitMatchesApplication(visitDto, reservedPublicApplication)
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity, PUBLIC)
    assertThat(visitEntity.getApplications().size).isEqualTo(1)
    assertThat(visitEntity.getLastApplication()?.reference).isEqualTo(applicationReference)
    assertThat(visitEntity.getLastApplication()?.applicationStatus).isEqualTo(ACCEPTED)

    assertBookedEvent(visitDto)
  }

  @Test
  fun `Book visit with an expired application`() {
    // Given
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 1)

    var expiredReservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, visitRestriction = OPEN)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference
    testApplicationRepository.updateTimestamp(LocalDateTime.now().minusDays(1), applicationReference)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    assertVisitMatchesApplication(visitDto, expiredReservedApplication)

    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertThat(visitEntity.getApplications().size).isEqualTo(1)
    assertThat(visitEntity.getLastApplication()?.reference).isEqualTo(applicationReference)
    assertThat(visitEntity.getLastApplication()?.applicationStatus).isEqualTo(ACCEPTED)

    // And
    assertBookedEvent(visitDto)
  }

  @Test
  fun `Book visit with an expired closed application with no open capacity and overbook allowed`() {
    // Given
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", closedCapacity = 0)

    var expiredReservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, visitRestriction = CLOSED)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference
    testApplicationRepository.updateTimestamp(LocalDateTime.now().minusDays(1), applicationReference)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, allowOverBooking = true, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `Booked visit twice by application reference - has one event and one visit`() {
    // Given
    val applicationReference = reservedStaffApplication.reference

    // When
    val responseSpec1 = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, visitorDetails = visitorDetails)
    val responseSpec2 = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, visitorDetails = visitorDetails)

    // Then
    val visit1 = createVisitDtoFromResponse(responseSpec1)
    val visit2 = createVisitDtoFromResponse(responseSpec2)

    assertThat(visit1.reference).isEqualTo(visit2.reference)
    assertThat(visit1.applicationReference).isEqualTo(visit2.applicationReference)
    assertThat(visit1.visitStatus).isEqualTo(visit2.visitStatus)
    assertThat(testVisitRepository.hasOneVisit(visit1.reference)).isTrue()

    // just one event thrown
    assertBookedEvent(visit1)
  }

  @Test
  fun `Book visit visit by application Reference - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val applicationReference = reservedStaffApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, authHttpHeaders, applicationReference, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Book visit visit by application Reference - unauthorised when no token`() {
    // Given
    val applicationReference = "12345"

    // When
    val responseSpec = webTestClient.post().uri(getVisitBookUrl(applicationReference))
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `when contact name and number is supplied in application for a new visit then visit will be booked with a contact name and number`() {
    var applicationWithContact = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      applicationStatus = IN_PROGRESS,
      reservedSlot = true,
      visitRestriction = OPEN,
    )

    // contact details have name and phone number
    val contact = ContactDto(name = "Aled Evans", telephone = "01348811539", email = "email@example.com")

    applicationEntityHelper.createContact(application = applicationWithContact, contact)
    applicationEntityHelper.createVisitor(application = applicationWithContact, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = applicationWithContact, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = applicationWithContact, description = "Some More Text")
    applicationWithContact = applicationEntityHelper.save(applicationWithContact)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationWithContact.reference, visitorDetails = visitorDetails)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.visitContact.name).isEqualTo(applicationWithContact.visitContact!!.name)
    assertThat(visitDto.visitContact.telephone).isEqualTo(applicationWithContact.visitContact!!.telephone)
    assertVisitMatchesApplication(visitDto, applicationWithContact)

    val application = testApplicationRepository.findByReference(visitDto.applicationReference!!)
    assertThat(application!!.applicationStatus).isEqualTo(ACCEPTED)

    // And
    assertBookedEvent(visitDto)
  }

  @Test
  fun `when phone number or email is not supplied in application for a new visit then visit will be booked with no phone number or email for contact`() {
    val applicationWithNoPhoneNumberNoEmail = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      applicationStatus = IN_PROGRESS,
      reservedSlot = true,
      visitRestriction = OPEN,
    )

    // contact details has name and no phone number
    val contact = ContactDto(name = "Aled Evans", telephone = null, email = null)

    applicationEntityHelper.createVisitor(application = applicationWithNoPhoneNumberNoEmail, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = applicationWithNoPhoneNumberNoEmail, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = applicationWithNoPhoneNumberNoEmail, description = "Some More Text")
    applicationEntityHelper.createContact(application = applicationWithNoPhoneNumberNoEmail, contact)
    applicationEntityHelper.save(applicationWithNoPhoneNumberNoEmail)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationWithNoPhoneNumberNoEmail.reference, visitorDetails = visitorDetails)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.visitContact).isNotNull()
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNull()
    assertVisitMatchesApplication(visitDto, applicationWithNoPhoneNumberNoEmail)

    val application = testApplicationRepository.findByReference(visitDto.applicationReference!!)
    assertThat(application!!.applicationStatus).isEqualTo(ACCEPTED)

    // And
    assertBookedEvent(visitDto)
  }

  @Test
  fun `Already completed application returns existing visit and no other action is performed`() {
    // Given
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val completedApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, applicationStatus = ACCEPTED)
    applicationEntityHelper.createContact(application = completedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = completedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = completedApplication, description = "Some Text")
    reservedStaffApplication = applicationEntityHelper.save(reservedStaffApplication)

    val visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, createApplication = false)
    visit.addApplication(completedApplication)

    visitEntityHelper.createNote(visit = visit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = visit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = visit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = visit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = visit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = visit, description = "Some Text")
    visitEntityHelper.save(visit)

    val applicationReference = completedApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody().returnResult().responseBody
    val visitDto = objectMapper.readValue(returnResult, VisitDto::class.java)

    assertVisitMatchesApplication(visitDto, completedApplication)

    verify(telemetryClient, times(0)).trackEvent(eq("visit-booked"), any(), isNull())
  }

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit, userType: UserType = STAFF, isRequestVisit: Boolean = false) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)

    if (isRequestVisit) {
      assertThat(eventAudit.type).isEqualTo(EventAuditType.REQUESTED_VISIT)
    } else {
      assertThat(eventAudit.type).isEqualTo(EventAuditType.BOOKED_VISIT)
    }

    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)

    if (STAFF == eventAudit.actionedBy.userType) {
      assertThat(eventAudit.actionedBy.userName).isEqualTo("booking_guy")
      assertThat(eventAudit.actionedBy.bookerReference).isNull()
    }
    if (PUBLIC == eventAudit.actionedBy.userType) {
      assertThat(eventAudit.actionedBy.userName).isNull()
      assertThat(eventAudit.actionedBy.bookerReference).isEqualTo("booking_guy")
    }

    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.PHONE)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isEqualTo(visitEntity.sessionSlot.sessionTemplateReference)
    assertThat(eventAudit.applicationReference).isEqualTo(visitEntity.getLastApplication()!!.reference)
  }

  @Test
  fun `when visit booked without visitor ages passed only visitor ids are sent to app insights`() {
    // Given
    val applicationReference = reservedStaffApplication.reference

    // When visitor ages not sent
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isOk
    // Then
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["visitors"]).isEqualTo("[{\"visitorId\":\"321\"},{\"visitorId\":\"322\"},{\"visitorId\":\"323\"}]")
      },
      isNull(),
    )
  }

  @Test
  fun `when visit booked without visitor ages passed for some visitors both ids and ages are sent to app insights`() {
    // Given
    val applicationReference = reservedStaffApplication.reference

    // When visitor sent for some visitors
    val visitorDetails = mutableSetOf<BookingRequestVisitorDetailsDto>()
    visitorDetails.add(BookingRequestVisitorDetailsDto(321L, 33))
    visitorDetails.add(BookingRequestVisitorDetailsDto(322L, 35))
    visitorDetails.add(BookingRequestVisitorDetailsDto(323L, null))

    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference, visitorDetails = visitorDetails)

    // Then
    responseSpec.expectStatus().isOk
    // Then
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["visitors"]).isEqualTo("[{\"visitorId\":\"321\",\"age\":33},{\"visitorId\":\"322\",\"age\":35},{\"visitorId\":\"323\"}]")
      },
      isNull(),
    )
  }

  private fun assertVisitMatchesApplication(visitDto: VisitDto, application: Application) {
    assertThat(visitDto.reference).isNotEmpty()
    assertThat(visitDto.applicationReference).isEqualTo(application.reference)
    assertThat(visitDto.prisonerId).isEqualTo(application.prisonerId)
    assertThat(visitDto.prisonCode).isEqualTo(sessionTemplateDefault.prison.code)
    assertThat(visitDto.visitRoom).isEqualTo(sessionTemplateDefault.visitRoom)
    assertThat(visitDto.startTimestamp)
      .isEqualTo(application.sessionSlot.slotStart)
    assertThat(visitDto.endTimestamp)
      .isEqualTo(application.sessionSlot.slotEnd)
    assertThat(visitDto.visitType).isEqualTo(application.visitType)
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    assertThat(visitDto.visitRestriction).isEqualTo(application.restriction)
    assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    if (application.visitContact != null) {
      assertThat(visitDto.visitContact.name).isEqualTo(application.visitContact!!.name)
      assertThat(visitDto.visitContact.telephone).isEqualTo(application.visitContact!!.telephone)
    } else {
      assertThat(visitDto.visitContact).isNull()
    }
    assertThat(visitDto.visitors.size).isEqualTo(application.visitors.size)
    assertThat(visitDto.visitors[0].nomisPersonId).isEqualTo(application.visitors[0].nomisPersonId)
    assertThat(visitDto.visitors[0].visitContact).isEqualTo(application.visitors[0].contact!!)
    assertThat(visitDto.visitorSupport?.description).isEqualTo(application.support?.description)
    assertThat(visitDto.createdTimestamp).isNotNull()
    assertThat(visitDto).isNotNull()
    assertThat(visitDto.userType).isEqualTo(application.userType)
  }

  private fun assertBookedEvent(visit: VisitDto, isRequestVisit: Boolean = false) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    val eventName = if (isRequestVisit) {
      "visit-requested"
    } else {
      "visit-booked"
    }

    verify(telemetryClient).trackEvent(
      eq(eventName),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        assertThat(it["visitSubStatus"]).isEqualTo(visit.visitSubStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(visit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo((visit.visitContact.telephone != null).toString())
        assertThat(it["hasEmail"]).isEqualTo((visit.visitContact.email != null).toString())
        assertThat(it["supportRequired"]).isEqualTo(visit.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(visit.visitors.size.toString())
        val visitors = visit.visitors.map { visitor -> TelemetryClientService.VisitorDetails(visitor.nomisPersonId.toString(), visitorDetails.firstOrNull { visitorDetails -> visitorDetails.visitorId == visitor.nomisPersonId }?.visitorAge) }
        assertThat(it["visitors"]).isEqualTo(objectMapper.writeValueAsString(visitors))
        eventAudit.actionedBy.userName?.let { value ->
          assertThat(it["actionedBy"]).isEqualTo(value)
        }
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq(eventName), any(), isNull())
  }

  private fun createVisitDtoFromResponse(responseSpec: ResponseSpec): VisitDto {
    val returnResult = responseSpec.expectStatus().isOk.expectBody().returnResult().responseBody
    return objectMapper.readValue(returnResult, VisitDto::class.java)
  }
}
