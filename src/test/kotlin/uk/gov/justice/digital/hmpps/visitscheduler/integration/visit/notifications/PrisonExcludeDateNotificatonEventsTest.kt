package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionSlotEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddPrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemovePrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.repository.TestVisitNotificationEventRepository
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
class PrisonExcludeDateNotificatonEventsTest : IntegrationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @SpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @Autowired
  lateinit var sessionSlotEntityHelper: SessionSlotEntityHelper

  @Autowired
  lateinit var testVisitNotificationEventRepository: TestVisitNotificationEventRepository

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  internal fun setUp() {
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER_CONFIG", "ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when add exclude date added then visits are flagged for review`() {
    // Given
    val prisonXYZ = prisonEntityHelper.create("XYZ")
    val prisonMSI = prisonEntityHelper.create("MSI")
    val sessionTemplateXYZ = sessionTemplateEntityHelper.create(prison = prisonXYZ, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))
    val sessionTemplateMSI = sessionTemplateEntityHelper.create(prison = prisonMSI, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))
    val excludeDate = LocalDate.now().plusDays(10)

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // existing visit for excludeDate in different prison (MDI)
    createApplicationAndVisit(sessionTemplate = sessionTemplateMSI, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // cancelled visit for excludeDate in same prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.CANCELLED, slotDate = excludeDate)
    // existing visit not for excludeDate in same prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate.plusDays(1))

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))

    val visitNotifications = testVisitNotificationEventRepository.findAll()

    // only 1 visit for the same date with status of BOOKED will be flagged.
    Assertions.assertThat(visitNotifications).hasSize(1)
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSamePrison.reference)
  }

  @Test
  fun `when exclude date is removed then visit flag is removed`() {
    // Given
    val prisonXYZ = prisonEntityHelper.create("XYZ")
    val excludeDate = LocalDate.now().plusDays(10)
    val sessionTemplateXYZ = sessionTemplateEntityHelper.create(prison = prisonXYZ, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))

    // existing visit for excludeDate in same prison
    val bookedVisitForSamePrison = createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisitForSamePrison.reference)

    // call remove exclude dates next
    responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate)
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(0)
    assertPrisonExcludeDateRemovalUnFlagEvent(bookedVisitForSamePrison.reference, UnFlagEventReason.PRISON_EXCLUDE_DATE_REMOVED)
  }

  @Test
  fun `when excluded date visit is updated to different slot then visit flag is removed`() {
    // Given
    val excludeDate = LocalDate.now().plusDays(10)
    val prison = sessionTemplateDefault.prison

    // existing visit for excludeDate in same prison
    val visit = createApplicationAndVisit(sessionTemplate = sessionTemplateDefault, slotDate = excludeDate)

    val newSessionSlot = sessionSlotEntityHelper.create(sessionTemplateDefault.reference, prison.id, LocalDate.now().plusDays(5), sessionTemplateDefault.startTime, sessionTemplateDefault.endTime)

    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate)
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(visit.reference)

    // create a reserveVisitSlotDto with start and end timestamp different from current visit
    val reserveVisitSlotDto = CreateApplicationDto(
      prisonerId = visit.prisonerId,
      sessionTemplateReference = newSessionSlot.sessionTemplateReference!!,
      sessionDate = newSessionSlot.slotDate,
      applicationRestriction = CreateApplicationRestriction.OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = "John Smith",
    )

    responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, visit.reference)

    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()

    val updatedApplication = objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)

    // When
    callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updatedApplication.reference)

    // Then
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prison.code, excludeDate))
    responseSpec.expectStatus().isCreated

    Assertions.assertThat(testVisitNotificationEventRepository.findAll()).isEmpty()
    assertPrisonExcludeDateRemovalUnFlagEvent(visit.reference, UnFlagEventReason.VISIT_DATE_UPDATED)
  }

  @Test
  fun `when excluded date visit is updated but date not changed then visit flag is not removed`() {
    // Given
    val prisonXYZ = prisonEntityHelper.create("XYZ")
    val excludeDate = LocalDate.now().plusDays(10)
    val sessionTemplateXYZ = sessionTemplateEntityHelper.create(prison = prisonXYZ, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))

    // existing visit for excludeDate in same prison
    val application = createApplicationAndSave(sessionTemplate = sessionTemplateXYZ, prisonCode = prisonXYZ.code, completed = true, slotDate = excludeDate)
    val bookedVisit = createVisitAndSave(VisitStatus.BOOKED, application, sessionTemplateXYZ)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate)

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications[0].bookingReference).isEqualTo(bookedVisit.reference)

    // create a reserveVisitSlotDto with sessionDAte same as current visit
    val reserveVisitSlotDto = CreateApplicationDto(
      prisonerId = bookedVisit.prisonerId,
      sessionTemplateReference = bookedVisit.sessionSlot.sessionTemplateReference!!,
      sessionDate = excludeDate,
      applicationRestriction = CreateApplicationRestriction.OPEN,
      visitContact = ContactDto("John Smith", "013448811538"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = "John Smith",
    )

    responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, bookedVisit.reference)

    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()

    val updatedApplication = objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)

    callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updatedApplication.reference)
    responseSpec.expectStatus().isCreated

    visitNotifications = testVisitNotificationEventRepository.findAll()
    Assertions.assertThat(visitNotifications).hasSize(1)
    verify(telemetryClient, times(0)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  fun assertPrisonExcludeDateRemovalUnFlagEvent(
    visitReference: String,
    unFlagEventReason: UnFlagEventReason,
  ) {
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        Assertions.assertThat(it["reference"]).isEqualTo(visitReference)
        Assertions.assertThat(it["reviewType"]).isEqualTo(NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE.reviewType)
        Assertions.assertThat(it["reason"]).isEqualTo(unFlagEventReason.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
