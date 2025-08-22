package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

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
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.client.ActivitiesApiClient
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_COURT_VIDEO_APPOINTMENT_UPDATED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.activities.ActivitiesAppointmentInstanceDetailsDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType.NOT_KNOWN
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.SupportedCourtVideoAppointmentCategoryCode
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UserType.SYSTEM
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.CourtVideoAppointmentNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatCourtVideoAppointmentUpdated
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_COURT_VIDEO_APPOINTMENT_UPDATED_PATH")
class CourtVideoAppointmentUpdatedVisitNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  @MockitoSpyBean
  private lateinit var activitiesApiClientSpy: ActivitiesApiClient

  val prisonerId = "A1234AA"
  val prisonCode = "ABC"
  val appointmentInstanceId = "123456"

  lateinit var prison1: Prison
  lateinit var sessionTemplate: SessionTemplate
  lateinit var sessionTemplateWindowBuffer: SessionTemplate
  lateinit var sessionTemplateOutsideOfNewWindow: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.parse("13:00"), endTime = LocalTime.parse("14:00"))
    sessionTemplateWindowBuffer = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.parse("16:15"), endTime = LocalTime.parse("18:15"))
    sessionTemplateOutsideOfNewWindow = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.parse("09:00"), endTime = LocalTime.parse("10:00"))
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when court video appointment is updated then existing flagged visits are un-flagged and future booked visits are flagged and saved`() {
    // Given
    val activitiesApiResponse = ActivitiesAppointmentInstanceDetailsDto(
      categoryCode = SupportedCourtVideoAppointmentCategoryCode.VLPM.toString(),
      appointmentDate = LocalDate.now().plusDays(1),
      startTime = "12:00",
      endTime = "16:00",
      prisonerNumber = prisonerId,
      prisonCode = prisonCode,
    )

    activitiesApiMockServer.stubGetAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId, result = activitiesApiResponse)

    val existingFlaggedVisit = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplateOutsideOfNewWindow,
      prisonerId = prisonerId,
    )
    val visit = visitEntityHelper.save(existingFlaggedVisit)
    eventAuditEntityHelper.create(existingFlaggedVisit)
    visitNotificationEventHelper.create(visit = visit, notificationEventType = NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT, notificationAttributes = mapOf(Pair(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID, appointmentInstanceId)))

    val notificationDto = CourtVideoAppointmentNotificationDto(
      appointmentInstanceId = appointmentInstanceId,
    )

    val visitToFlag = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
    )

    visitEntityHelper.save(visitToFlag)
    eventAuditEntityHelper.create(visitToFlag)

    // When
    val responseSpec = callNotifyVSiPThatCourtVideoAppointmentUpdated(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(visitToFlag), NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT)

    verify(activitiesApiClientSpy, times(1)).getAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId)
    verify(visitNotificationEventRepository, times(1)).saveAndFlush(any<VisitNotificationEvent>())

    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.COURT_VIDEO_APPOINTMENT_UPDATED.desc)
      },
      isNull(),
    )

    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    assertThat(visitNotifications[0].visit.reference).isEqualTo(visitToFlag.reference)
    assertThat(visitNotifications[0].visitNotificationEventAttributes.size).isEqualTo(1)
    assertThat(visitNotifications[0].visitNotificationEventAttributes[0].attributeName).isEqualTo(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID)
    assertThat(visitNotifications[0].visitNotificationEventAttributes[0].attributeValue).isEqualTo(appointmentInstanceId)

    val auditEvents = testEventAuditRepository.getAuditByType(EventAuditType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT)
    assertThat(auditEvents).hasSize(1)
    with(auditEvents[0]) {
      assertThat(bookingReference).isEqualTo(visitToFlag.reference)
      assertThat(applicationReference).isEqualTo(visitToFlag.getLastApplication()?.reference)
      assertThat(sessionTemplateReference).isEqualTo(visitToFlag.sessionSlot.sessionTemplateReference)
      assertThat(type).isEqualTo(EventAuditType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT)
      assertThat(applicationMethodType).isEqualTo(NOT_KNOWN)
      assertThat(actionedBy.userType).isEqualTo(SYSTEM)
      assertThat(actionedBy.bookerReference).isNull()
      assertThat(actionedBy.userName).isNull()
    }
  }

  @Test
  fun `when court video appointment is updated but is not a supported then processing is skipped`() {
    // Given
    val activitiesApiResponse = ActivitiesAppointmentInstanceDetailsDto(
      categoryCode = "NOT_SUPPORTED",
      appointmentDate = LocalDate.now().plusDays(1),
      startTime = "12:00",
      endTime = "16:00",
      prisonerNumber = prisonerId,
      prisonCode = prisonCode,
    )

    activitiesApiMockServer.stubGetAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId, result = activitiesApiResponse)

    val notificationDto = CourtVideoAppointmentNotificationDto(
      appointmentInstanceId = appointmentInstanceId,
    )

    // When
    val responseSpec = callNotifyVSiPThatCourtVideoAppointmentUpdated(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(), NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT)

    verify(activitiesApiClientSpy, times(1)).getAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId)
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }

  @Test
  fun `when court video appointment is updated but 404 is returned from activities api then processing is skipped`() {
    // Given
    activitiesApiMockServer.stubGetAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId, result = null)

    val notificationDto = CourtVideoAppointmentNotificationDto(
      appointmentInstanceId = appointmentInstanceId,
    )

    // When
    val responseSpec = callNotifyVSiPThatCourtVideoAppointmentUpdated(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    assertFlaggedVisitEvent(listOf(), NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT)

    verify(activitiesApiClientSpy, times(1)).getAppointmentInstanceDetails(appointmentInstanceId = appointmentInstanceId)
    verify(visitNotificationEventRepository, times(0)).saveAndFlush(any<VisitNotificationEvent>())
  }
}
