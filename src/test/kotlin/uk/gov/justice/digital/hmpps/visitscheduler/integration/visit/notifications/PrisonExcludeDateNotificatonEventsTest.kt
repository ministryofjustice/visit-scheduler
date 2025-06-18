package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.ContactDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.VisitorDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.ApplicationSupportDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.application.CreateApplicationDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationStatus.ACCEPTED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.PRISON_VISITS_BLOCKED_FOR_DATE
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SessionRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.STAFF
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitSubStatus
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.PrisonDateBlockedDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.SessionSlotEntityHelper
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callAddPrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callApplicationForVisitChange
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callRemovePrisonExcludeDate
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callVisitBook
import uk.gov.justice.digital.hmpps.visitscheduler.service.VisitNotificationEventService
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
class PrisonExcludeDateNotificatonEventsTest : NotificationTestBase() {

  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @MockitoSpyBean
  lateinit var visitNotificationEventServiceSpy: VisitNotificationEventService

  @Autowired
  lateinit var sessionSlotEntityHelper: SessionSlotEntityHelper

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
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.CANCELLED, visitSubStatus = VisitSubStatus.CANCELLED, slotDate = excludeDate)
    // existing visit not for excludeDate in same prison
    createApplicationAndVisit(sessionTemplate = sessionTemplateXYZ, visitStatus = VisitStatus.BOOKED, slotDate = excludeDate.plusDays(1))

    // When
    val responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate, actionedBy = "TEST_USER")

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))

    val visitNotifications = testVisitNotificationEventRepository.findAll()

    // only 1 visit for the same date with status of BOOKED will be flagged.
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(bookedVisitForSamePrison.reference)

    val auditEvents = testEventAuditRepository.getAuditByType(PRISON_VISITS_BLOCKED_FOR_DATE)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(actionedBy.userName).isNull()
      assertThat(bookingReference).isEqualTo(bookedVisitForSamePrison.reference)
      assertThat(applicationReference).isEqualTo(bookedVisitForSamePrison.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(bookedVisitForSamePrison.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(PRISON_VISITS_BLOCKED_FOR_DATE)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
    }
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
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate, actionedBy = "TEST_USER")

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    assertThat(visitNotifications[0].visit.reference).isEqualTo(bookedVisitForSamePrison.reference)

    // call remove exclude dates next
    responseSpec = callRemovePrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate, actionedBy = "TEST_USER")
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleRemovePrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    visitNotifications = testVisitNotificationEventRepository.findAll()
    assertThat(visitNotifications).hasSize(0)
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
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prison.code, excludeDate, actionedBy = "TEST_USER")
    responseSpec.expectStatus().isOk

    val visitNotifications = testVisitNotificationEventRepository.findAll()
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visit.reference)

    // create a reserveVisitSlotDto with start and end timestamp different from current visit
    val reserveVisitSlotDto = CreateApplicationDto(
      prisonerId = visit.prisonerId,
      sessionTemplateReference = newSessionSlot.sessionTemplateReference!!,
      sessionDate = newSessionSlot.slotDate,
      applicationRestriction = SessionRestriction.OPEN,
      visitContact = ContactDto("John Smith", "013448811538", "email@example.com"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = "John Smith",
      userType = STAFF,
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

    assertThat(testVisitNotificationEventRepository.findAll()).isEmpty()
    assertPrisonExcludeDateRemovalUnFlagEvent(visit.reference, UnFlagEventReason.VISIT_UPDATED)
  }

  @Test
  fun `when excluded date visit is updated and date changed then all visit flags are removed`() {
    // Given
    val prisonXYZ = prisonEntityHelper.create("XYZ")
    val excludeDate = LocalDate.now().plusDays(10)
    val sessionTemplateXYZ = sessionTemplateEntityHelper.create(prison = prisonXYZ, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))

    // existing visit for excludeDate in same prison
    val application = createApplicationAndSave(sessionTemplate = sessionTemplateXYZ, prisonCode = prisonXYZ.code, applicationStatus = ACCEPTED, slotDate = excludeDate)
    val bookedVisit = createVisitAndSave(VisitStatus.BOOKED, visitSubStatus = VisitSubStatus.AUTO_APPROVED, application, sessionTemplateXYZ)

    // When
    // call add exclude dates first
    var responseSpec = callAddPrisonExcludeDate(webTestClient, roleVisitSchedulerHttpHeaders, prisonXYZ.code, excludeDate, actionedBy = "TEST_USER")

    // Then
    responseSpec.expectStatus().isOk
    verify(visitNotificationEventServiceSpy, times(1)).handleAddPrisonVisitBlockDate(PrisonDateBlockedDto(prisonXYZ.code, excludeDate))
    var visitNotifications = testVisitNotificationEventRepository.findAll()
    assertThat(visitNotifications[0].visit.reference).isEqualTo(bookedVisit.reference)

    // create a reserveVisitSlotDto with sessionDAte same as current visit
    val reserveVisitSlotDto = CreateApplicationDto(
      prisonerId = bookedVisit.prisonerId,
      sessionTemplateReference = bookedVisit.sessionSlot.sessionTemplateReference!!,
      sessionDate = excludeDate.plusWeeks(1),
      applicationRestriction = SessionRestriction.OPEN,
      visitContact = ContactDto("John Smith", "013448811538", "email@example.com"),
      visitors = setOf(VisitorDto(123, true), VisitorDto(124, false)),
      visitorSupport = ApplicationSupportDto("Some Text"),
      actionedBy = "John Smith",
      userType = STAFF,
    )

    responseSpec = callApplicationForVisitChange(webTestClient, roleVisitSchedulerHttpHeaders, reserveVisitSlotDto, bookedVisit.reference)

    val returnResult = responseSpec.expectStatus().isCreated
      .expectBody()
      .returnResult()

    val updatedApplication = objectMapper.readValue(returnResult.responseBody, ApplicationDto::class.java)

    callVisitBook(webTestClient, roleVisitSchedulerHttpHeaders, updatedApplication.reference)
    responseSpec.expectStatus().isCreated

    visitNotifications = testVisitNotificationEventRepository.findAll()
    assertThat(visitNotifications).hasSize(0)
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }

  fun assertPrisonExcludeDateRemovalUnFlagEvent(
    visitReference: String,
    unFlagEventReason: UnFlagEventReason,
  ) {
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      org.mockito.kotlin.check {
        assertThat(it["reference"]).isEqualTo(visitReference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.PRISON_VISITS_BLOCKED_FOR_DATE.reviewType)
        assertThat(it["reason"]).isEqualTo(unFlagEventReason.desc)
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(eq("unflagged-visit-event"), any(), isNull())
  }
}
