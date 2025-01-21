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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitUpdate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class UpdateVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedStaffApplication: Application

  private lateinit var reservedPublicApplication: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedStaffApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false)
    applicationEntityHelper.createContact(application = reservedStaffApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedStaffApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedStaffApplication, description = "Some Text")
    reservedStaffApplication = applicationEntityHelper.save(reservedStaffApplication)

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false, userType = PUBLIC)
    applicationEntityHelper.createContact(application = reservedPublicApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedPublicApplication, description = "Some Text")
    reservedPublicApplication = applicationEntityHelper.save(reservedPublicApplication)
  }

  @Test
  fun `Book visit with an open application with no open capacity throws exception - with existing visit and ignores reserved application`() {
    // Given
    val visitRestriction = OPEN
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 1)

    val existingVisit = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)
    createApplicationAndSave(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)

    var expiredReservedApplication = applicationEntityHelper.create(slotDate = existingVisit.sessionSlot.slotDate, sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Book visit with an close application with no close capacity throws exception - with existing visit and ignores reserved application`() {
    // Given
    val visitRestriction = CLOSED
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", closedCapacity = 1)

    val existingVisit = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)
    createApplicationAndSave(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)

    var expiredReservedApplication = applicationEntityHelper.create(slotDate = existingVisit.sessionSlot.slotDate, sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Book visit with an expired open application with no open capacity throw exception - existing visit and reserved application`() {
    // Given
    val visitRestriction = OPEN
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 2)

    val existingVisit = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)
    createApplicationAndSave(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)

    var expiredReservedApplication = applicationEntityHelper.create(slotDate = existingVisit.sessionSlot.slotDate, sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference
    testApplicationRepository.updateTimestamp(LocalDateTime.now().minusDays(1), applicationReference)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Book visit with an expired closed application with no remaining close capacity throw exception - existing visit and reserved application`() {
    // Given
    val visitRestriction = CLOSED
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", closedCapacity = 2)

    val existingVisit = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)
    createApplicationAndSave(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)

    var expiredReservedApplication = applicationEntityHelper.create(slotDate = existingVisit.sessionSlot.slotDate, sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference
    testApplicationRepository.updateTimestamp(LocalDateTime.now().minusDays(1), applicationReference)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Updated Booking with application with no open capacity but no change to slot or restriction books normally`() {
    // Given
    val visitRestriction = OPEN
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 1)
    val bookingToUpdate = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)

    val newSlotDate = bookingToUpdate.sessionSlot.slotDate.plusWeeks(1)
    var expiredReservedApplication = applicationEntityHelper.create(slotDate = newSlotDate, sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")
    expiredReservedApplication.visit = bookingToUpdate
    expiredReservedApplication.visitId = bookingToUpdate.id

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isOk
  }

  @Test
  fun `Updated Booking with application with no open capacity with change to restriction throws exception`() {
    // Given
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 1, closedCapacity = 1)
    val bookingToUpdate = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = OPEN)
    createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = CLOSED)

    var expiredReservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = CLOSED)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")
    expiredReservedApplication.visit = bookingToUpdate
    expiredReservedApplication.visitId = bookingToUpdate.id

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Updated Booking with application with no open capacity with change to slot throws exception`() {
    // Given
    val visitRestriction = OPEN
    val sessionTemplateDefault = sessionTemplateEntityHelper.create(prisonCode = "DFT", openCapacity = 1, closedCapacity = 1)
    val bookingToUpdate = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction)
    val otherSlotDate = bookingToUpdate.sessionSlot.slotDate.plusWeeks(1)
    createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, visitRestriction = visitRestriction, slotDate = otherSlotDate)

    var expiredReservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false, visitRestriction = visitRestriction, slotDate = otherSlotDate)
    applicationEntityHelper.createContact(application = expiredReservedApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredReservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredReservedApplication, description = "Some Text")
    expiredReservedApplication.visit = bookingToUpdate
    expiredReservedApplication.visitId = bookingToUpdate.id

    expiredReservedApplication = applicationEntityHelper.save(expiredReservedApplication)

    val applicationReference = expiredReservedApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    assertHelper.assertBookingCapacityError(responseSpec)
  }

  @Test
  fun `Update visit by application Reference - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val applicationReference = reservedStaffApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, authHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Update visit by application Reference - unauthorised when no token`() {
    // Given
    val applicationReference = "12345"

    // When
    val responseSpec = webTestClient.post().uri(getVisitBookUrl(applicationReference))
      .exchange()

    // Then
    responseSpec.expectStatus().isUnauthorized
  }

  @Test
  fun `Amend and book visit for staff`() {
    // Given

    // Original application and visit
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val originalVisit = createApplicationAndVisit(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault)

    var newApplication = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = if (originalVisit.visitRestriction == OPEN) CLOSED else OPEN,
    )

    applicationEntityHelper.createContact(application = newApplication, name = "Aled Evans", phone = "01348811539", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some Other Text")
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertVisitMatchesApplication(visitDto, newApplication)

    val application = testApplicationRepository.findByReference(visitDto.applicationReference)
    assertThat(application!!.completed).isTrue()

    // And
    assertBookedEvent(visitDto, true)
  }

  @Test
  fun `Amend and book visit for when staff updates a public user visit`() {
    // Given

    // Original application and visit
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val originalVisit = createApplicationAndVisit(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, userType = PUBLIC)

    var newApplication = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = if (originalVisit.visitRestriction == OPEN) CLOSED else OPEN,
    )

    applicationEntityHelper.createContact(application = newApplication, name = "Aled Evans", phone = "01348811539", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some Other Text")
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference, userType = UserType.STAFF)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertThat(visitDto).isNotNull()
    assertThat(visitDto.userType).isNotEqualTo(newApplication.userType)
    assertThat(visitDto.userType).isEqualTo(PUBLIC)
    assertThat(newApplication.userType).isEqualTo(UserType.STAFF)
    val application = testApplicationRepository.findByReference(visitDto.applicationReference)
    assertThat(application!!.completed).isTrue()

    // And
    assertBookedEvent(visitDto, true)
  }

  @Test
  fun `when no phone number or email is supplied in new application for existing visit then updated visit will not have a phone number or email`() {
    // Given

    // Original application and visit has a contact number
    val slotDateInThePast = LocalDate.now().plusDays(1)
    var contact = ContactDto(name = "Test User", telephone = "011111111111", email = null)

    val originalVisit = createApplicationAndVisit(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, visitContact = contact)
    assertThat(originalVisit.visitContact!!.name).isEqualTo(contact.name)
    assertThat(originalVisit.visitContact!!.telephone).isEqualTo(contact.telephone)

    var newApplication = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = if (originalVisit.visitRestriction == OPEN) CLOSED else OPEN,
    )

    // creating visitor and new contact with no phone number or email
    contact = ContactDto(name = "Test User", telephone = null, email = null)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some More Text")
    applicationEntityHelper.createContact(application = newApplication, contact)
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertThat(visitDto.visitContact).isNotNull()
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNull()
    assertThat(visitDto.visitContact.email).isNull()
    assertVisitMatchesApplication(visitDto, newApplication)
  }

  @Test
  fun `when contact is supplied in new application for existing visit without a contact then booked visit will have a contact`() {
    // Given

    // Original application and visit do not have phone number or email
    var contact = ContactDto(name = "Test User", telephone = null, email = null)
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val originalVisit = createApplicationAndVisit(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, visitContact = contact)
    assertThat(originalVisit.visitContact!!.name).isEqualTo(contact.name)
    assertThat(originalVisit.visitContact!!.telephone).isNull()

    var newApplication = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = if (originalVisit.visitRestriction == OPEN) CLOSED else OPEN,
    )

    // creating visitor and new contact with phone number and email
    contact = ContactDto(name = "Test User", telephone = "01111111111", email = "email@example.com")
    applicationEntityHelper.createContact(application = newApplication, contact)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some More Text")
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)
    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNotNull()
    assertThat(visitDto.visitContact.telephone).isEqualTo(contact.telephone)
    assertThat(visitDto.visitContact.email).isNotNull()
    assertThat(visitDto.visitContact.email).isEqualTo(contact.email)
    assertVisitMatchesApplication(visitDto, newApplication)
  }

  @Test
  fun `Already completed application returns existing visit and no other action is performed`() {
    // Given
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val completedApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, completed = true)
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
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    val returnResult = responseSpec.expectStatus().isOk.expectBody().returnResult().responseBody
    val visitDto = objectMapper.readValue(returnResult, VisitDto::class.java)

    assertVisitMatchesApplication(visitDto, completedApplication)

    verify(telemetryClient, times(0)).trackEvent(eq("visit-booked"), any(), isNull())
  }

  @Test
  fun `Amend and book expired visit - returns bad request error `() {
    // Given
    val slotDateInThePast = LocalDate.now().minusYears(1)
    val expiredApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, completed = false)
    applicationEntityHelper.createContact(application = expiredApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = expiredApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredApplication, description = "Some Text")
    applicationEntityHelper.save(reservedStaffApplication)

    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault)
    expiredVisit.addApplication(expiredApplication)

    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = expiredVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = expiredVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = expiredVisit, description = "Some Text")
    visitEntityHelper.save(expiredVisit)

    val reference = expiredVisit.reference
    val applicationReference = expiredApplication.reference

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - $reference is in the past, it cannot be changed")
  }

  @Test
  fun `when visit session not changed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    existingVisit = visitEntityHelper.save(existingVisit)

    var updateVisitApplication = applicationEntityHelper.create(existingVisit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo("false")
        assertThat(it["hasDateChanged"]).isEqualTo("false")
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo("false")
        assertThat(it["hasNeedsChanged"]).isEqualTo("false")
        assertThat(it["hasContactsChanged"]).isEqualTo("false")
      },
      isNull(),
    )
  }

  @Test
  fun `when visit session changed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    val sessionTemplate1 = sessionTemplateEntityHelper.create(prisonCode = "DFT", startTime = sessionTemplateDefault.startTime.plusHours(1), endTime = sessionTemplateDefault.endTime.plusHours(1))
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    existingVisit = visitEntityHelper.save(existingVisit)

    // move visit to a different session on the same day
    var updateVisitApplication = applicationEntityHelper.create(prisonerId = existingVisit.prisonerId, sessionTemplate = sessionTemplate1, slotDate = existingVisit.sessionSlot.slotDate)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(true.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(false.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when visit date changed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    existingVisit = visitEntityHelper.save(existingVisit)

    // move visit to a different session on the same day
    var updateVisitApplication = applicationEntityHelper.create(prisonerId = existingVisit.prisonerId, sessionTemplate = sessionTemplateDefault, slotDate = existingVisit.sessionSlot.slotDate.plusWeeks(1))
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(true.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(true.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when visit date and visitors changed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )

    val visitor1Id: Long = 1
    val visitor2Id: Long = 2
    val visitor3Id: Long = 3
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(existingVisit, visitor1Id, true)
    visitEntityHelper.createVisitor(existingVisit, visitor2Id, false)
    visitEntityHelper.createVisitor(existingVisit, visitor3Id, false)
    existingVisit = visitEntityHelper.save(existingVisit)

    // move visit to a different session on the same day
    var updateVisitApplication = applicationEntityHelper.create(prisonerId = existingVisit.prisonerId, sessionTemplate = sessionTemplateDefault, slotDate = existingVisit.sessionSlot.slotDate.plusWeeks(1))
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id

    // visitor 2 is now the main contact
    applicationEntityHelper.createVisitor(updateVisitApplication, visitor1Id, false)
    applicationEntityHelper.createVisitor(updateVisitApplication, visitor2Id, true)
    applicationEntityHelper.createVisitor(updateVisitApplication, visitor3Id, false)
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(true.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(true.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(true.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when a visitor is removed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )

    val visitor1Id: Long = 1
    val visitor2Id: Long = 2
    val visitor3Id: Long = 3
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(existingVisit, visitor1Id, true)
    visitEntityHelper.createVisitor(existingVisit, visitor2Id, false)
    visitEntityHelper.createVisitor(existingVisit, visitor3Id, false)
    existingVisit = visitEntityHelper.save(existingVisit)

    // move visit to a different session on the same day
    var updateVisitApplication = applicationEntityHelper.create(existingVisit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id

    // visitor 3 removed
    applicationEntityHelper.createVisitor(updateVisitApplication, visitor1Id, true)
    applicationEntityHelper.createVisitor(updateVisitApplication, visitor2Id, false)
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(false.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(false.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(true.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when special needs are added check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )

    val visitor1Id: Long = 1
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(existingVisit, visitor1Id, true)
    existingVisit = visitEntityHelper.save(existingVisit)

    // update visit to add support
    var updateVisitApplication = applicationEntityHelper.create(existingVisit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id

    applicationEntityHelper.createVisitor(updateVisitApplication, visitor1Id, true)

    // needs added
    applicationEntityHelper.createSupport(updateVisitApplication, "Wheelchair")
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(false.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(false.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(true.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when special needs are removed check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )

    val visitor1Id: Long = 1
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(existingVisit, visitor1Id, true)
    visitEntityHelper.createSupport(existingVisit, "Wheelchair")
    existingVisit = visitEntityHelper.save(existingVisit)

    // update visit to remove support
    var updateVisitApplication = applicationEntityHelper.create(existingVisit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id

    applicationEntityHelper.createVisitor(updateVisitApplication, visitor1Id, true)
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(false.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(false.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(true.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(false.toString())
      },
      isNull(),
    )
  }

  @Test
  fun `when contact  number is updated check update specific visit flags sent to telemetry service`() {
    // visit for prisoner already exists
    var existingVisit = visitEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      slotDate = startDate,
    )

    val visitor1Id: Long = 1
    visitEntityHelper.createContact(visit = existingVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(existingVisit, visitor1Id, true)
    existingVisit = visitEntityHelper.save(existingVisit)

    // update visit to remove support
    var updateVisitApplication = applicationEntityHelper.create(existingVisit)
    updateVisitApplication.completed = false
    updateVisitApplication.visitId = existingVisit.id
    applicationEntityHelper.createContact(updateVisitApplication, ContactDto(name = "Jane Doe", telephone = "01234 098765", email = null))

    applicationEntityHelper.createVisitor(updateVisitApplication, visitor1Id, true)
    updateVisitApplication = applicationEntityHelper.save(updateVisitApplication)

    // When
    val responseSpec = callVisitUpdate(webTestClient, roleVisitSchedulerHttpHeaders, updateVisitApplication.reference)
    responseSpec.expectStatus().isOk
    val visitDto = getVisitDto(responseSpec)
    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitDto.reference)
        assertThat(it["hasSessionChanged"]).isEqualTo(false.toString())
        assertThat(it["hasDateChanged"]).isEqualTo(false.toString())
        assertThat(it["existingVisitSession"]).isEqualTo(existingVisit.sessionSlot.slotStart.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["newVisitSession"]).isEqualTo(visitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["hasVisitorsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasNeedsChanged"]).isEqualTo(false.toString())
        assertThat(it["hasContactsChanged"]).isEqualTo(true.toString())
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

  private fun assertBookedEvent(visit: VisitDto, isUpdated: Boolean) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(visit.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo((visit.visitContact.telephone != null).toString())
        assertThat(it["hasEmail"]).isEqualTo((visit.visitContact.email != null).toString())
        assertThat(it["isUpdated"]).isEqualTo(isUpdated.toString())
        assertThat(it["supportRequired"]).isEqualTo(visit.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(visit.visitors.size.toString())
        val commaDelimitedVisitorIds = visit.visitors.map { it.nomisPersonId }.joinToString(",")
        assertThat(it["visitors"]).isEqualTo(commaDelimitedVisitorIds)
        eventAudit.actionedBy.userName?.let { value ->
          assertThat(it["actionedBy"]).isEqualTo(value)
        }
        assertThat(it["source"]).isEqualTo(eventAudit.actionedBy.userType.name)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())
  }

  private fun createVisitDtoFromResponse(responseSpec: ResponseSpec): VisitDto {
    val returnResult = responseSpec.expectStatus().isOk.expectBody().returnResult().responseBody
    return objectMapper.readValue(returnResult, VisitDto::class.java)
  }
}
