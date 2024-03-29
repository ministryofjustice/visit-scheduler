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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.CLOSED
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction.OPEN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK")
class BookVisitTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @Autowired
  private lateinit var testApplicationRepository: TestApplicationRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedApplication: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, completed = false)
    applicationEntityHelper.createContact(application = reservedApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = reservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedApplication, description = "Some Text")
    reservedApplication = applicationEntityHelper.save(reservedApplication)
  }

  @Test
  fun `Book visit visit by application Reference`() {
    // Given
    val applicationReference = reservedApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    assertVisitMatchesApplication(visitDto, reservedApplication)

    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertThat(visitEntity.getApplications().size).isEqualTo(1)
    assertThat(visitEntity.getLastApplication()?.reference).isEqualTo(applicationReference)
    assertThat(visitEntity.getLastApplication()?.completed).isTrue()

    // And
    assertBookedEvent(visitDto, false)
  }

  @Test
  fun `Booked visit twice by application reference - has one event and one visit`() {
    // Given
    val applicationReference = reservedApplication.reference

    // When
    val responseSpec1 = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)
    val responseSpec2 = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    val visit1 = createVisitDtoFromResponse(responseSpec1)
    val visit2 = createVisitDtoFromResponse(responseSpec2)

    assertThat(visit1.reference).isEqualTo(visit2.reference)
    assertThat(visit1.applicationReference).isEqualTo(visit2.applicationReference)
    assertThat(visit1.visitStatus).isEqualTo(visit2.visitStatus)
    assertThat(testVisitRepository.hasOneVisit(visit1.reference)).isTrue()

    // just one event thrown
    assertBookedEvent(visit1, false)
  }

  @Test
  fun `Book visit visit by application Reference - access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())
    val applicationReference = reservedApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, authHttpHeaders, applicationReference)

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
  fun `Amend and book visit`() {
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

    applicationEntityHelper.createContact(application = newApplication, name = "Aled Evans", phone = "01348811539")
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some Other Text")
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

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
  fun `when contact name and number is supplied in application for a new visit then visit will be booked with a contact name and number`() {
    var applicationWithContact = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = OPEN,
    )

    // contact details have name and phone number
    val contact = ContactDto(name = "Aled Evans", telephone = "01348811539")

    applicationEntityHelper.createContact(application = applicationWithContact, contact)
    applicationEntityHelper.createVisitor(application = applicationWithContact, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = applicationWithContact, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = applicationWithContact, description = "Some More Text")
    applicationWithContact = applicationEntityHelper.save(applicationWithContact)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationWithContact.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.visitContact.name).isEqualTo(applicationWithContact.visitContact!!.name)
    assertThat(visitDto.visitContact.telephone).isEqualTo(applicationWithContact.visitContact!!.telephone)
    assertVisitMatchesApplication(visitDto, applicationWithContact)

    val application = testApplicationRepository.findByReference(visitDto.applicationReference)
    assertThat(application!!.completed).isTrue()

    // And
    assertBookedEvent(visitDto, false)
  }

  @Test
  fun `when phone number is not supplied in application for a new visit then visit will be booked with no phone number for contact`() {
    val applicationWithNoPhoneNumber = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = OPEN,
    )

    // contact details has name and no phone number
    val contact = ContactDto(name = "Aled Evans", telephone = null)

    applicationEntityHelper.createVisitor(application = applicationWithNoPhoneNumber, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = applicationWithNoPhoneNumber, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = applicationWithNoPhoneNumber, description = "Some More Text")
    applicationEntityHelper.createContact(application = applicationWithNoPhoneNumber, contact)
    applicationEntityHelper.save(applicationWithNoPhoneNumber)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationWithNoPhoneNumber.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.visitContact).isNotNull()
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNull()
    assertVisitMatchesApplication(visitDto, applicationWithNoPhoneNumber)

    val application = testApplicationRepository.findByReference(visitDto.applicationReference)
    assertThat(application!!.completed).isTrue()

    // And
    assertBookedEvent(visitDto, false)
  }

  @Test
  fun `when phone number is not supplied in new application for existing visit then updated visit will not have a phone number`() {
    // Given

    // Original application and visit has a contact number
    val slotDateInThePast = LocalDate.now().plusDays(1)
    var contact = ContactDto(name = "Test User", telephone = "011111111111")

    val originalVisit = createApplicationAndVisit(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, visitContact = contact)
    assertThat(originalVisit.visitContact!!.name).isEqualTo(contact.name)
    assertThat(originalVisit.visitContact!!.telephone).isEqualTo(contact.telephone)

    var newApplication = applicationEntityHelper.create(
      sessionTemplate = sessionTemplateDefault,
      completed = false,
      reservedSlot = true,
      visitRestriction = if (originalVisit.visitRestriction == OPEN) CLOSED else OPEN,
    )

    // creating visitor and new contact with no phone number
    contact = ContactDto(name = "Test User", telephone = null)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some More Text")
    applicationEntityHelper.createContact(application = newApplication, contact)
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertThat(visitDto.visitContact).isNotNull()
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNull()
    assertVisitMatchesApplication(visitDto, newApplication)
  }

  @Test
  fun `when contact is supplied in new application for existing visit without a contact then booked visit will have a contact`() {
    // Given

    // Original application and visit do not have phone number
    var contact = ContactDto(name = "Test User", telephone = null)
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

    // creating visitor and new contact with phone number
    contact = ContactDto(name = "Test User", telephone = "01111111111")
    applicationEntityHelper.createContact(application = newApplication, contact)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 123L, visitContact = true)
    applicationEntityHelper.createVisitor(application = newApplication, nomisPersonId = 666L, visitContact = false)
    applicationEntityHelper.createSupport(application = newApplication, description = "Some More Text")
    newApplication = applicationEntityHelper.save(newApplication)

    originalVisit.addApplication(newApplication)

    visitEntityHelper.save(originalVisit)
    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, newApplication.reference)

    // Then
    val visitDto = createVisitDtoFromResponse(responseSpec)
    assertThat(visitDto.reference).isEqualTo(originalVisit.reference)
    assertThat(visitDto.visitContact.name).isEqualTo(contact.name)
    assertThat(visitDto.visitContact.telephone).isNotNull()
    assertThat(visitDto.visitContact.telephone).isEqualTo(contact.telephone)
    assertVisitMatchesApplication(visitDto, newApplication)
  }

  private fun createVisitDtoFromResponse(responseSpec: ResponseSpec): VisitDto {
    val returnResult = responseSpec.expectStatus().isOk.expectBody().returnResult().responseBody
    return objectMapper.readValue(returnResult, VisitDto::class.java)
  }

  @Test
  fun `Already completed application returns existing visit and no other action is performed`() {
    // Given
    val slotDateInThePast = LocalDate.now().plusDays(1)
    val completedApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, completed = true)
    applicationEntityHelper.createContact(application = completedApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = completedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = completedApplication, description = "Some Text")
    reservedApplication = applicationEntityHelper.save(reservedApplication)

    var visit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, createApplication = false)
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

  @Test
  fun `Amend and book expired visit - returns bad request error `() {
    // Given
    val slotDateInThePast = LocalDate.now().minusYears(1)
    val expiredApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplateDefault, completed = false)
    applicationEntityHelper.createContact(application = expiredApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = expiredApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredApplication, description = "Some Text")
    applicationEntityHelper.save(reservedApplication)

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
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    responseSpec
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("Validation failure: trying to change / cancel an expired visit")
      .jsonPath("$.developerMessage").isEqualTo("Visit with booking reference - $reference is in the past, it cannot be changed")
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
        assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        assertThat(it["isUpdated"]).isEqualTo(isUpdated.toString())
        assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy)
        assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
        assertThat(it["supportRequired"]).isEqualTo(visit.visitorSupport?.description)
        assertThat(it["hasPhoneNumber"]).isEqualTo((visit.visitContact.telephone != null).toString())
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())
  }
}
