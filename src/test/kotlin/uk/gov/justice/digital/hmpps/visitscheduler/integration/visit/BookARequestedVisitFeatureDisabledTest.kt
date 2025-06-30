package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_BOOK
import uk.gov.justice.digital.hmpps.visitscheduler.dto.BookingRequestDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.IN_PROGRESS
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.PUBLIC
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prison.api.VisitBalancesDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.prisonersearch.PrisonerSearchResultDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $VISIT_BOOK requested visit feature - disabled")
@TestPropertySource(properties = ["feature.request-booking-enabled=false"])
class BookARequestedVisitFeatureDisabledTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  private lateinit var reservedPublicApplication: Application

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))

    reservedPublicApplication = applicationEntityHelper.create(sessionTemplate = sessionTemplateDefault, applicationStatus = IN_PROGRESS, userType = PUBLIC)
    applicationEntityHelper.createContact(application = reservedPublicApplication, name = "Jane Doe", phone = "01234 098765", email = "email@example.com")
    applicationEntityHelper.createVisitor(application = reservedPublicApplication, nomisPersonId = 321L, visitContact = true)
    applicationEntityHelper.createSupport(application = reservedPublicApplication, description = "Some Text")
    reservedPublicApplication = applicationEntityHelper.save(reservedPublicApplication)
  }

  @Test
  fun `Book a requested visit feature disabled (booked via public application)`() {
    // Given
    val prisonerId = reservedPublicApplication.prisonerId
    val applicationReference = reservedPublicApplication.reference
    val prisonerDto = PrisonerSearchResultDto(prisonerNumber = prisonerId, prisonId = reservedPublicApplication.prison.code)
    prisonOffenderSearchMockServer.stubGetPrisoner(prisonerId, prisonerDto)
    prisonApiMockServer.stubGetVisitBalances(prisonerId, VisitBalancesDto(remainingVo = 5, remainingPvo = 5))

    // When
    val responseSpec = callVisitBook(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      applicationReference,
      userType = PUBLIC,
      bookingRequestDto = BookingRequestDto("booking_guy", ApplicationMethodType.PHONE, false, PUBLIC, true),
    )

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

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit, userType: UserType = STAFF) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)
    assertThat(eventAudit.type).isEqualTo(EventAuditType.BOOKED_VISIT)
    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(userType)
    assertThat(eventAudit.actionedBy.userName).isNull()
    assertThat(eventAudit.actionedBy.bookerReference).isEqualTo("booking_guy")
    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.PHONE)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isEqualTo(visitEntity.sessionSlot.sessionTemplateReference)
    assertThat(eventAudit.applicationReference).isEqualTo(visitEntity.getLastApplication()!!.reference)
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
    assertThat(visitDto.visitSubStatus).isEqualTo(VisitSubStatus.AUTO_APPROVED)
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

  private fun assertBookedEvent(visit: VisitDto) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      check {
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
}
