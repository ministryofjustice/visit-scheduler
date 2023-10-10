package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import uk.gov.justice.digital.hmpps.visitscheduler.service.NotificationEventType

@Repository
interface VisitNotificationEventRepository : JpaRepository<VisitNotificationEvent, Int> {

  @Query(
    "SELECT count(ve) > 0" +
      " FROM visit_notification_event ve " +
      " WHERE ve.create_timestamp BETWEEN NOW() - INTERVAL '10 MINUTE' AND NOW() " +
      " AND ve.booking_reference=:bookingReference AND ve.type=:#{#notificationEvent.name()}",
    nativeQuery = true,
  )
  fun isEventARecentDuplicate(
    bookingReference: String,
    notificationEvent: NotificationEventType,
  ): Boolean
}
