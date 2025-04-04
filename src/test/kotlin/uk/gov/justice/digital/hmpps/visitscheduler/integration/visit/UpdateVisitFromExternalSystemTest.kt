package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.catalina.User
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
import uk.gov.justice.digital.hmpps.visitscheduler.controller.PUT_VISIT_FROM_EXTERNAL_SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.SnsDomainEventPublishDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.UpdateVisitFromExternalSystemDto
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
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callUpdateVisitFromExternalSystem
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.SnsService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Transactional(propagation = SUPPORTS)
@DisplayName("PUT $PUT_VISIT_FROM_EXTERNAL_SYSTEM")
class UpdateVisitFromExternalSystemTest : IntegrationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @Autowired
  private lateinit var testVisitRepository: TestVisitRepository

  @MockitoSpyBean
  private lateinit var telemetryClient: TelemetryClient

  @MockitoSpyBean
  private lateinit var snsService: SnsService

  private val reference = "v9-d7-ed-7u"
  private val updateVisitFromExternalSystemDto = UpdateVisitFromExternalSystemDto(
    visitRoom = "B1",
    visitType = VisitType.SOCIAL,
    visitRestriction = VisitRestriction.CLOSED,
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
    val responseSpec = callUpdateVisitFromExternalSystem(webTestClient, authHttpHeaders, reference, updateVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isForbidden

    // And
    verify(telemetryClient, times(1)).trackEvent(eq("visit-access-denied-error"), any(), isNull())
  }

  @Test
  fun `Update a visit from an external system`() {
    // Given
    val visit = visitEntityHelper.create(
      prisonCode = "MKI",
      visitRoom = "A1",
      slotDate = LocalDate.now(),
      visitStart = LocalTime.now(),
      visitEnd = LocalTime.now().plusHours(1),
      visitType = VisitType.SOCIAL,
      activePrison = true,
      createContact = true,
      createApplication = false
    )

    // When
    val responseSpec = callUpdateVisitFromExternalSystem(webTestClient, roleVisitSchedulerHttpHeaders, visit.reference, updateVisitFromExternalSystemDto)

    // Then
    responseSpec.expectStatus().isOk

    val visitDto = getVisitDto(responseSpec)

    // Then
    val visitEntity = testVisitRepository.findByReference(visitDto.reference)
    assertAuditEvent(visitDto, visitEntity)

    // And
    assertUpdatedEvent(visit, visitDto)
  }

  @Test
  fun `Return 404 when visit not found`() {
    // When
    val responseSpec = callUpdateVisitFromExternalSystem(
      webTestClient,
      roleVisitSchedulerHttpHeaders,
      reference,
      updateVisitFromExternalSystemDto
    )

    // Then
    responseSpec.expectStatus().isNotFound
  }

  private fun assertAuditEvent(visitDto: VisitDto, visitEntity: Visit) {
    val eventAudit = this.eventAuditRepository.findLastEventByBookingReference(visitDto.reference)
    assertThat(eventAudit.type).isEqualTo(EventAuditType.UPDATED_VISIT)
    assertThat(eventAudit.actionedBy).isNotNull()
    assertThat(eventAudit.actionedBy.userType).isEqualTo(UserType.PRISONER)
    assertThat(eventAudit.actionedBy.userName).isEqualTo(visitDto.prisonerId)
    assertThat(eventAudit.actionedBy.bookerReference).isNull()
    assertThat(eventAudit.applicationMethodType).isEqualTo(ApplicationMethodType.BY_PRISONER)
    assertThat(eventAudit.bookingReference).isEqualTo(visitEntity.reference)
    assertThat(eventAudit.sessionTemplateReference).isNull()
  }

  private fun assertUpdatedEvent(visit: Visit, updatedVisitDto: VisitDto) {
    val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    verify(telemetryClient).trackEvent(
      eq("visit-booked"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(updatedVisitDto.reference)
        assertThat(it["applicationReference"]).isEqualTo(updatedVisitDto.applicationReference)
        assertThat(it["prisonerId"]).isEqualTo(updatedVisitDto.prisonerId)
        assertThat(it["prisonId"]).isEqualTo(updatedVisitDto.prisonCode)
        assertThat(it["visitStatus"]).isEqualTo(updatedVisitDto.visitStatus.name)
        assertThat(it["visitRestriction"]).isEqualTo(updatedVisitDto.visitRestriction.name)
        assertThat(it["visitStart"]).isEqualTo(updatedVisitDto.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitEnd"]).isEqualTo(updatedVisitDto.endTimestamp.format(DateTimeFormatter.ISO_DATE_TIME))
        assertThat(it["visitType"]).isEqualTo(updatedVisitDto.visitType.name)
        assertThat(it["visitRoom"]).isEqualTo(updatedVisitDto.visitRoom)
        assertThat(it["hasPhoneNumber"]).isEqualTo((updatedVisitDto.visitContact.telephone != null).toString())
        assertThat(it["hasEmail"]).isEqualTo((updatedVisitDto.visitContact.email != null).toString())
        assertThat(it["supportRequired"]).isEqualTo(updatedVisitDto.visitorSupport?.description)
        assertThat(it["totalVisitors"]).isEqualTo(updatedVisitDto.visitors.size.toString())
        assertThat(it["visitors"]).isEqualTo(updatedVisitDto.visitors.map { visitor -> visitor.nomisPersonId }.joinToString(","))
        assertThat(it["actionedBy"]).isEqualTo(updatedVisitDto.prisonerId)
        assertThat(it["source"]).isEqualTo(UserType.PRISONER.toString())
        assertThat(it["applicationMethodType"]).isEqualTo(ApplicationMethodType.BY_PRISONER.toString())
        assertThat(it["isUpdated"]).isEqualTo("true")
        assertThat(it["supportRequired"]).isEqualTo(updateVisitFromExternalSystemDto.visitorSupport?.description)
        assertThat(it["hasSessionChanged"]).isEqualTo("true")
        assertThat(it["hasDateChanged"]).isEqualTo("true")
        assertThat(it["existingVisitSession"]).isEqualTo(visit.sessionSlot.slotStart.format(datetimeFormatter))
        assertThat(it["newVisitSession"]).isEqualTo(  updatedVisitDto.startTimestamp.format(datetimeFormatter))
        assertThat(it["hasVisitorsChanged"]).isEqualTo("true")
        assertThat(it["hasNeedsChanged"]).isEqualTo("true")
        assertThat(it["hasContactsChanged"]).isEqualTo("true")
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("visit-booked"), any(), isNull())

    verify(snsService, times(1)).sendChangedVisitBookedEvent(any<SnsDomainEventPublishDto>())
  }
}
