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
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.POST_VISIT_FROM_EXTERNAL_SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.CreateVisitFromExternalSystemDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitNoteDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitNoteType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitType
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callCreateVisitFromExternalSystem
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $POST_VISIT_FROM_EXTERNAL_SYSTEM")
class CreateVisitFromExternalSystemTest: IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  private val createVisitFromExternalSystemDto = CreateVisitFromExternalSystemDto(
    prisonerId = "AF34567G",
    prisonId = "HEI",
    clientVisitReference = "client-visit-reference-1",
    visitRoom = "A1",
    visitType = VisitType.SOCIAL,
    visitRestriction = VisitRestriction.OPEN,
    startTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
    endTimestamp = LocalDateTime.parse("2018-12-01T13:45:00"),
    visitContact = ContactDto(
      name = "John Smith",
      telephone = "01234567890",
      email = "email@example.com",
    ),
    visitNotes = listOf(
      VisitNoteDto(
        type = VisitNoteType.VISITOR_CONCERN,
        text = "Visitor is concerned that his mother in-law is coming!",
      ),
    ),
    createDateTime = LocalDateTime.parse("2018-12-01T13:45:00"),
    visitors = setOf(
      VisitorDto(nomisPersonId = 1234, visitContact = true),
      VisitorDto(nomisPersonId = 4321, visitContact = false),
    ),
    visitorSupport = VisitorSupportDto(
      description = "Visually impaired assistance",
    ),
  )

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `Access forbidden when no role`() {
    // Given
    val authHttpHeaders = setAuthorisation(roles = listOf())

    // When
    val responseSpec = callCreateVisitFromExternalSystem(webTestClient, authHttpHeaders, createVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Create a visit from an external system`() {
    // Given

    // When
    val responseSpec = callCreateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, createVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity)

    // And
    assertBookedEvent(visitDto)
  }

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)
    assertThat(eventAudit.type).isEqualTo(EventAuditType.BOOKED_VISIT)
    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(UserType.PRISONER)
    assertThat(eventAudit.actionedBy.userName).isEqualTo(visitDto.prisonerId)
    assertThat(eventAudit.actionedBy.bookerReference).isNull()
    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.BY_PRISONER)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
  }

  private fun assertBookedEvent(visit: VisitDto) {
    val eventAudit = eventAuditRepository.findLastEventByBookingReference(visit.reference)

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
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
        assertThat(it["supportRequired"]).isEqualTo(visit.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(visit.visitors.size.toString())
        val commaDelimitedVisitorIds = visit.visitors.map { visitor -> visitor.nomisPersonId }.joinToString(",")
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

    verify(snsService, times(1)).sendVisitBookedEvent(any<SnsDomainEventPublishDto>())
  }
}