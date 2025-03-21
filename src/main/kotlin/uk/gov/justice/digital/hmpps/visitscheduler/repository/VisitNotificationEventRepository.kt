package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.NotificationEventType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent
import java.time.LocalDate

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
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE ss.slot_date >= NOW() " +
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
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.reference  = vne.booking_reference  " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " JOIN visit_visitor vv on vv.visit_id = v.id " +
      " WHERE ss.slot_date >= NOW() " +
      " AND v.prisoner_id = :prisonerNumber " +
      " AND p.code = :prisonCode " +
      " AND vv.nomis_person_id = :visitorId " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.reference, vne.id",
    nativeQuery = true,
  )
  fun getEventsByVisitor(
    prisonerNumber: String,
    prisonCode: String,
    visitorId: Long,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.reference  = vne.booking_reference  " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE ss.slot_date >= :slotDate" +
      " AND ss.slot_date < (CAST(:slotDate AS DATE) + CAST('1 day' AS INTERVAL))" +
      " AND p.code = :prisonCode " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.reference, vne.id",
    nativeQuery = true,
  )
  fun getEventsByVisitDate(
    prisonCode: String,
    slotDate: LocalDate,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.reference  = vne.booking_reference  " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN session_template st on ss.session_template_reference  = st.reference " +
      " WHERE ss.slot_date >= :slotDate" +
      " AND ss.slot_date < (CAST(:slotDate AS DATE) + CAST('1 day' AS INTERVAL))" +
      " AND st.reference = :sessionTemplateReference " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.reference, vne.id",
    nativeQuery = true,
  )
  fun getEventsBySessionAndVisitDate(
    sessionTemplateReference: String,
    slotDate: LocalDate,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    "SELECT sum(ng) FROM (   " +
      "SELECT count(distinct vne.reference) as ng FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id AND p.code = :prisonCode " +
      " WHERE  v.visit_status = 'BOOKED' AND ss.slot_start >= NOW()   " +
      " GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroupsCountByPrisonCode(prisonCode: String): Int?

  @Query(
    "SELECT sum(ng) FROM (   " +
      "SELECT count(distinct vne.reference) as ng FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id AND p.code = :prisonCode " +
      " WHERE  v.visit_status = 'BOOKED' AND ss.slot_start >= NOW() AND " +
      " vne.type in (:notificationEventTypes) " +
      " GROUP BY vne.reference) sq ",
    nativeQuery = true,
  )
  fun getNotificationGroupsCountByPrisonCode(prisonCode: String, notificationEventTypes: List<String>): Int?

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference " +
      " JOIN prison p on p.id  = v.prison_id  AND p.code= :prisonCode " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " WHERE v.visit_status = 'BOOKED' AND ss.slot_start >= NOW()  " +
      " ORDER BY ss.slot_date, vne.reference, v.id",
    nativeQuery = true,
  )
  fun getFutureVisitNotificationEvents(@Param("prisonCode") prisonCode: String): List<VisitNotificationEvent>

  @Query(
    "select vne.* FROM visit_notification_event vne " +
      "JOIN visit_notification_event_attribute vnea on vne.id = vnea.visit_notification_event_id " +
      "WHERE vnea.attribute_name = 'PAIRED_VISIT' " +
      "AND vnea.attribute_value = :visitReference",
    nativeQuery = true,
  )
  fun getPairedVisitNotificationEvents(visitReference: String): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.type FROM visit_notification_event vne WHERE vne.booking_reference=:bookingReference GROUP by id",
    nativeQuery = true,
  )
  fun getNotificationsTypesForBookingReference(@Param("bookingReference") bookingReference: String): List<NotificationEventType>

  fun getVisitNotificationEventsByBookingReference(
    bookingReference: String,
  ): List<VisitNotificationEvent>

  fun getVisitNotificationEventsByReference(
    reference: String,
  ): List<VisitNotificationEvent>

  fun deleteByBookingReference(@Param("bookingReference") bookingReference: String): Int

  fun deleteByReference(@Param("reference") reference: String): Int
}
