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

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.reference  = vne.booking_reference  " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE v.visit_start >= NOW() " +
      " AND v.prisoner_id = :prisonerNumber " +
      " AND p.code = :prisonCode " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.reference, vne.id",
    nativeQuery = true,
  )
  fun getEventsBy(
    prisonerNumber: String,
    prisonCode: String,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    "SELECT sum(ng) FROM (   " +
      "SELECT count(distinct vne.reference) as ng FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference  " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE v.visit_start >= NOW() " +
      "  AND p.code = :prisonCode GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroupsByPrisonCode(prisonCode: String): Int?

  @Query(
    "SELECT sum(ng) FROM (   " +
      "SELECT count(distinct vne.reference) as ng FROM visit_notification_event vne " +
      "   JOIN visit v ON v.reference  = vne.booking_reference " +
      "   WHERE v.visit_start >= NOW() GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroups(): Int?
}
