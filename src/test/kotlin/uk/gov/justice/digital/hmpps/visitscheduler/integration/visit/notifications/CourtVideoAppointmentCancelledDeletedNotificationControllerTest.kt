package uk.gov.justice.digital.hmpps.visitscheduler.integration.visit.notifications

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.http.HttpHeaders
import org.springframework.transaction.annotation.Propagation.SUPPORTS
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.controller.VISIT_NOTIFICATION_VISITOR_APPROVED_PATH
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.UnFlagEventReason
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus.BOOKED
import uk.gov.justice.digital.hmpps.visitscheduler.dto.visitnotification.CourtVideoAppointmentNotificationDto
import uk.gov.justice.digital.hmpps.visitscheduler.helper.callNotifyVSiPThatCourtVideoAppointmentCancelledDeleted
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Prison
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = SUPPORTS)
@DisplayName("POST $VISIT_NOTIFICATION_VISITOR_APPROVED_PATH")
class CourtVideoAppointmentCancelledDeletedNotificationControllerTest : NotificationTestBase() {
  private lateinit var roleVisitSchedulerHttpHeaders: (HttpHeaders) -> Unit

  val prisonerId = "A1234AA"
  val prisonCode = "ABC"
  val appointmentInstanceId = "123456"

  lateinit var prison1: Prison
  lateinit var sessionTemplate: SessionTemplate
  lateinit var sessionTemplateWindowBuffer: SessionTemplate

  @BeforeEach
  internal fun setUp() {
    prison1 = prisonEntityHelper.create(prisonCode = prisonCode)
    sessionTemplate = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.parse("13:00"), endTime = LocalTime.parse("14:00"))
    sessionTemplateWindowBuffer = sessionTemplateEntityHelper.create(prison = prison1, startTime = LocalTime.parse("16:15"), endTime = LocalTime.parse("18:15"))
    roleVisitSchedulerHttpHeaders = setAuthorisation(roles = listOf("ROLE_VISIT_SCHEDULER"))
  }

  @Test
  fun `when court video appointment is cancelled or deleted then flagged visits are un-flagged`() {
    // Given
    val notificationDto = CourtVideoAppointmentNotificationDto(
      appointmentInstanceId = appointmentInstanceId,
    )

    val visit1 = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(1),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
    )
    val visit = visitEntityHelper.save(visit1)
    eventAuditEntityHelper.create(visit1)
    visitNotificationEventHelper.create(visit = visit, notificationEventType = NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT, notificationAttributes = mapOf(Pair(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID, appointmentInstanceId)))

    val visit2NotToBeUnflagged = createApplicationAndVisit(
      slotDate = LocalDate.now().plusDays(5),
      visitStatus = BOOKED,
      sessionTemplate = sessionTemplate,
      prisonerId = prisonerId,
    )
    val visitNotTobeUnflagged = visitEntityHelper.save(visit2NotToBeUnflagged)
    eventAuditEntityHelper.create(visitNotTobeUnflagged)
    visitNotificationEventHelper.create(visit = visitNotTobeUnflagged, notificationEventType = NotificationEventType.PRISONER_RELEASED_EVENT)

    // When
    val responseSpec = callNotifyVSiPThatCourtVideoAppointmentCancelledDeleted(webTestClient, roleVisitSchedulerHttpHeaders, notificationDto)

    // Then
    responseSpec.expectStatus().isOk
    val visitNotifications = testVisitNotificationEventRepository.findAllOrderById()
    assertThat(visitNotifications).hasSize(1)
    verify(telemetryClient).trackEvent(
      eq("unflagged-visit-event"),
      check {
        assertThat(it["reference"]).isEqualTo(visit.reference)
        assertThat(it["reviewTypes"]).isEqualTo(NotificationEventType.COURT_VIDEO_APPOINTMENT_CANCELLED_DELETED_EVENT.reviewType)
        assertThat(it["reason"]).isEqualTo(UnFlagEventReason.COURT_VIDEO_APPOINTMENT_CANCELLED_OR_DELETED.desc)
      },
      isNull(),
    )
  }
}
