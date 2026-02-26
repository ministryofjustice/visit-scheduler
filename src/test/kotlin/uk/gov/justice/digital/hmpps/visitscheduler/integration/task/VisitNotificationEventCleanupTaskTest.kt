package uk.gov.justice.digital.hmpps.visitscheduler.integration.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.builder.NotifyHistoryDtoBuilder
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventAttributeType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.helper.VisitNotificationEventHelper
import uk.gov.justice.digital.hmpps.visitscheduler.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.visitscheduler.task.VisitNotificationEventCleanupTask
import java.time.LocalDate
import java.time.LocalTime

@Transactional(propagation = Propagation.SUPPORTS)
@DisplayName("Visit notification event cleanup tests")
class VisitNotificationEventCleanupTaskTest : IntegrationTestBase() {
  @Autowired
  private lateinit var notifyHistoryDtoBuilder: NotifyHistoryDtoBuilder

  @Autowired
  private lateinit var visitNotificationEventCleanupTask: VisitNotificationEventCleanupTask

  @Autowired
  private lateinit var visitNotificationEventHelper: VisitNotificationEventHelper

  @BeforeEach
  fun setupData() {
    deleteEntityHelper.deleteAll()
  }

  @Test
  fun `when session report called for a report date then a valid session report is returned`() {
    // Given
    val prison1 = prisonEntityHelper.create("ABC", activePrison = true, excludeDates = emptyList())
    val sessionTemplate1Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = LocalDate.now().minusDays(7), validToDate =  LocalDate.now().minusDays(2))
    val sessionTemplate2Prison1 = sessionTemplateEntityHelper.create(prison = prison1, validFromDate = LocalDate.now().minusDays(7), validToDate =  LocalDate.now().plusDays(7))

    // visit 1 - In the past (3 days)
    val expiredVisitMarkedForRemoval = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate1Prison1, slotDate = LocalDate.now().minusDays(3), visitStart = LocalTime.of(15, 0), visitEnd = LocalTime.of(16, 0))

    // visit 2 - In the past (0 days)
    val todayVisit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate1Prison1, slotDate = LocalDate.now(), visitStart = LocalTime.of(15, 0), visitEnd = LocalTime.of(16, 0))

    // visit 3 - In the future
    val futureVisit = visitEntityHelper.create(prisonCode = prison1.code, visitStatus = VisitStatus.BOOKED, sessionTemplate = sessionTemplate2Prison1, slotDate = LocalDate.now().plusDays(1), visitStart = LocalTime.of(15, 0), visitEnd = LocalTime.of(16, 0))

    visitNotificationEventHelper.create(visit = expiredVisitMarkedForRemoval, notificationEventType = NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT, notificationAttributes = mapOf(Pair(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID, "12345")))
    visitNotificationEventHelper.create(visit = todayVisit, notificationEventType = NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT, notificationAttributes = mapOf(Pair(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID, "56789")))
    visitNotificationEventHelper.create(visit = futureVisit, notificationEventType = NotificationEventType.COURT_VIDEO_APPOINTMENT_CREATED_OR_UPDATED_EVENT, notificationAttributes = mapOf(Pair(NotificationEventAttributeType.APPOINTMENT_INSTANCE_ID, "98765")))

    // when
    visitNotificationEventCleanupTask.deleteOutdatedVisitNotificationEvents()

    // Then
    val remainingNotificationEvents = visitNotificationEventHelper.getAllVisitNotifications()

    assertThat(remainingNotificationEvents.size).isEqualTo(2)
    assertThat(remainingNotificationEvents.map { it.visit.reference }).containsExactlyInAnyOrder(todayVisit.reference, futureVisit.reference)

    assertThat(remainingNotificationEvents.map { it.visitNotificationEventAttributes }.size).isEqualTo(2)
  }
}
