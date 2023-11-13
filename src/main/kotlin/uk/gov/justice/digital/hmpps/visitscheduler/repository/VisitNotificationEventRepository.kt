package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

  /**
   * The inner query will only return one True or no Result because of the HAVING COUNT(reference)=2 and LIMIT 1 the outer query will then
   * return true if a result otherwise false. If we did not have the outer query we would get a null pointer.
   *
   * This is a very efficient way of doing these calculations using code will be inefficient
   */
  @Query(
    "SELECT count(*) = 1 from (SELECT COUNT(*) = 2 FROM visit_notification_event vne " +
      " WHERE vne.create_timestamp BETWEEN NOW() - INTERVAL '10 MINUTE' AND NOW() AND " +
      "   vne.booking_reference in (:bookingReferencePair1,:bookingReferencePair2) AND " +
      "   vne.type=:#{#notificationEvent.name()} " +
      " GROUP BY reference HAVING COUNT(reference)=2 LIMIT 1) as tmp",
    nativeQuery = true,
  )
  fun isEventARecentPairedDuplicate(
    bookingReferencePair1: String,
    bookingReferencePair2: String,
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
      " JOIN visit v ON v.reference  = vne.booking_reference AND v.visit_status = 'BOOKED' AND v.visit_start >= NOW()  " +
      " JOIN prison p on p.id  = v.prison_id AND p.code = :prisonCode " +
      " GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroupsCountByPrisonCode(prisonCode: String): Int?

  @Query(
    "SELECT sum(ng) FROM (   " +
      "SELECT count(distinct vne.reference) as ng FROM visit_notification_event vne " +
      "   JOIN visit v ON v.reference  = vne.booking_reference AND v.visit_status = 'BOOKED' AND v.visit_start >= NOW() " +
      "   GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroupsCount(): Int?

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference AND v.visit_status = 'BOOKED' AND v.visit_start >= NOW() " +
      " JOIN prison p on p.id  = v.prison_id  AND p.code= :prisonCode " +
      " ORDER BY v.visit_start,vne.reference",
    nativeQuery = true,
  )
  fun getFutureVisitNotificationEvents(@Param("prisonCode") prisonCode: String): List<VisitNotificationEvent>
}
