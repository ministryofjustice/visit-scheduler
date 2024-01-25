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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.helper.getVisitBookUrl
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISITOR_CONCERN
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_COMMENT
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitNoteType.VISIT_OUTCOMES
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestApplicationRepository
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
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

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedApplication: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplate)
    applicationEntityHelper.createContact(application = reservedApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = reservedApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedApplication, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(reservedApplication)
  }

  // TODO Must create a test class for the case were the booking is allready booked.

  @Test
  fun `Book visit visit by application Reference`() {
    // Given
    val applicationReference = reservedApplication.reference

    // When
    val responseSpec = callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, applicationReference)

    // Then
    val returnResult = responseSpec
      .expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    assertVisitMatchesApplication(visitDto, reservedApplication)

    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    Assertions.assertThat(visitEntity.applications.size).isEqualTo(1)
    Assertions.assertThat(visitEntity.applications[0]).isEqualTo(applicationReference)

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
    val returnResult1 = responseSpec1.expectStatus().isOk.expectBody().returnResult().responseBody
    val returnResult2 = responseSpec2.expectStatus().isOk.expectBody().returnResult().responseBody

    val visit1 = objectMapper.readValue(returnResult1, VisitDto::class.java)
    val visit2 = objectMapper.readValue(returnResult2, VisitDto::class.java)

    Assertions.assertThat(visit1.reference).isEqualTo(visit2.reference)
    Assertions.assertThat(visit1.applicationReference).isEqualTo(visit2.applicationReference)
    Assertions.assertThat(visit1.visitStatus).isEqualTo(visit2.visitStatus)
    Assertions.assertThat(testVisitRepository.hasOneVisit(visit1.reference)).isTrue()

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
  fun `Amend and book expired visit - returns bad request error `() {
    val slotDateInThePast = LocalDate.of((LocalDateTime.now().year - 1), 11, 1)
    val expiredApplication = applicationEntityHelper.create(slotDate = slotDateInThePast, sessionTemplate = sessionTemplate)
    applicationEntityHelper.createContact(application = expiredApplication, name = "Jane Doe", phone = "01234 098765")
    applicationEntityHelper.createVisitor(application = expiredApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = expiredApplication, name = "OTHER", details = "Some Text")
    applicationEntityHelper.save(reservedApplication)

    val expiredVisit = visitEntityHelper.create(visitStatus = BOOKED, slotDate = slotDateInThePast, sessionTemplate = sessionTemplate)
    expiredVisit.applications.add(expiredApplication)

    // Given
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text outcomes", type = VISIT_OUTCOMES)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text concerns", type = VISITOR_CONCERN)
    visitEntityHelper.createNote(visit = expiredVisit, text = "Some text comment", type = VISIT_COMMENT)
    visitEntityHelper.createContact(visit = expiredVisit, name = "Jane Doe", phone = "01234 098765")
    visitEntityHelper.createVisitor(visit = expiredVisit, nomisPersonId = 321L, visitContact = true)
    visitEntityHelper.createSupport(visit = expiredVisit, name = "OTHER", details = "Some Text")
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
    Assertions.assertThat(visitDto.reference).isNotEmpty()
    Assertions.assertThat(visitDto.applicationReference).isEqualTo(application.reference)
    Assertions.assertThat(visitDto.prisonerId).isEqualTo(application.prisonerId)
    Assertions.assertThat(visitDto.prisonCode).isEqualTo(application.prison.code)
    Assertions.assertThat(visitDto.visitRoom).isEqualTo("you tell me")
    Assertions.assertThat(visitDto.startTimestamp)
      .isEqualTo(application.sessionSlot.slotDate.atTime(application.sessionSlot.slotTime))
    Assertions.assertThat(visitDto.endTimestamp)
      .isEqualTo(application.sessionSlot.slotDate.atTime(application.sessionSlot.slotEndTime))
    Assertions.assertThat(visitDto.visitType).isEqualTo(application.visitType)
    Assertions.assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visitDto.visitRestriction).isEqualTo(application.restriction)
    Assertions.assertThat(visitDto.visitStatus).isEqualTo(BOOKED)
    Assertions.assertThat(visitDto.visitContact!!.name).isEqualTo(application.visitContact!!.name)
    Assertions.assertThat(visitDto.visitContact!!.telephone).isEqualTo(application.visitContact!!.telephone)
    Assertions.assertThat(visitDto.visitors.size).isEqualTo(application.visitors.size)
    Assertions.assertThat(visitDto.visitors[0].nomisPersonId).isEqualTo(application.visitors[0].nomisPersonId)
    Assertions.assertThat(visitDto.visitors[0].visitContact).isEqualTo(application.visitors[0].contact!!)
    Assertions.assertThat(visitDto.visitorSupport.size).isEqualTo(application.support.size)
    Assertions.assertThat(visitDto.visitorSupport[0].type).isEqualTo(application.support[0].type)
    Assertions.assertThat(visitDto.visitorSupport[0].text).isEqualTo(application.support[0].text!!)
    Assertions.assertThat(visitDto.createdTimestamp).isNotNull()
  }

  private fun assertBookedEvent(visit: VisitDto, isUpdated: Boolean) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visit.reference)
        Assertions.assertThat(it["applicationReference"]).isEqualTo(visit.applicationReference)
        Assertions.assertThat(it["prisonerId"]).isEqualTo(visit.prisonerId)
        Assertions.assertThat(it["prisonId"]).isEqualTo(visit.prisonCode)
        Assertions.assertThat(it["visitType"]).isEqualTo(visit.visitType.name)
        Assertions.assertThat(it["visitRoom"]).isEqualTo(visit.visitRoom)
        Assertions.assertThat(it["visitRestriction"]).isEqualTo(visit.visitRestriction.name)
        Assertions.assertThat(it["visitStart"]).isEqualTo(visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        Assertions.assertThat(it["visitStatus"]).isEqualTo(visit.visitStatus.name)
        Assertions.assertThat(it["isUpdated"]).isEqualTo(isUpdated.toString())
        Assertions.assertThat(it["actionedBy"]).isEqualTo(eventAudit.actionedBy)
        Assertions.assertThat(it["applicationMethodType"]).isEqualTo(eventAudit.applicationMethodType.name)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())
  }
}
