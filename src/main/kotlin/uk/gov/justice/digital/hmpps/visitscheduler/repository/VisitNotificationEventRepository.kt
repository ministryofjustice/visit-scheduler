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
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.id  = vne.visit_id  " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE ss.slot_date >= NOW() " +
      " AND v.prisoner_id = :prisonerNumber " +
      " AND p.code = :prisonCode " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.id",
    nativeQuery = true,
  )
  fun getEventsBy(
    prisonerNumber: String,
    prisonCode: String,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v on v.id  = vne.visit_id  " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " JOIN visit_visitor vv on vv.visit_id = v.id " +
      " WHERE ss.slot_date >= NOW() " +
      " AND v.prisoner_id = :prisonerNumber " +
      " AND p.code = :prisonCode " +
      " AND vv.nomis_person_id = :visitorId " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.id",
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
      " JOIN visit v on v.id  = vne.visit_id " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id " +
      " WHERE ss.slot_date >= :slotDate" +
      " AND ss.slot_date < (CAST(:slotDate AS DATE) + CAST('1 day' AS INTERVAL))" +
      " AND p.code = :prisonCode " +
      " AND vne.type=:#{#notificationEvent.name()}" +
      " ORDER BY vne.id",
    nativeQuery = true,
  )
  fun getEventsByVisitDate(
    prisonCode: String,
    slotDate: LocalDate,
    notificationEvent: NotificationEventType,
  ): List<VisitNotificationEvent>

  @Query(
    value =
    "SELECT COUNT(DISTINCT v.id) " +
      "FROM visit_notification_event vne " +
      " JOIN visit v ON v.id = vne.visit_id " +
      " JOIN session_slot ss ON ss.id = v.session_slot_id " +
      " JOIN prison p ON p.id = v.prison_id AND p.code = :prisonCode " +
      "WHERE v.visit_status = 'BOOKED' " +
      "AND v.visit_sub_status IN ('APPROVED', 'AUTO_APPROVED') " +
      "AND ss.slot_start  >= NOW()",
    nativeQuery = true,
  )
  fun getNotificationGroupsCountByPrisonCode(prisonCode: String): Int?

  @Query(
    "SELECT COUNT(DISTINCT v.reference) " +
      "FROM visit_notification_event vne " +
      " JOIN visit v ON v.id = vne.visit_id " +
      " JOIN session_slot ss ON ss.id = v.session_slot_id " +
      " JOIN prison p ON p.id = v.prison_id AND p.code = :prisonCode " +
      "WHERE v.visit_status = 'BOOKED' " +
      "AND v.visit_sub_status IN ('APPROVED', 'AUTO_APPROVED') " +
      "  AND ss.slot_start >= NOW() " +
      "  AND vne.type IN (:notificationEventTypes)",
    nativeQuery = true,
  )
  fun getNotificationGroupsCountByPrisonCode(prisonCode: String, notificationEventTypes: List<String>): Int?

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      "JOIN visit v ON v.id = vne.visit_id " +
      "JOIN session_slot ss ON ss.id = v.session_slot_id " +
      "WHERE ss.slot_start <= (NOW() - INTERVAL '1 day') " +
      "ORDER BY ss.slot_start, v.id",
    nativeQuery = true,
  )
  fun findExpiredVisitNotificationEvents(): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v ON v.id  = vne.visit_id " +
      " JOIN prison p on p.id  = v.prison_id  AND p.code= :prisonCode " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      "WHERE v.visit_status = 'BOOKED' " +
      "AND v.visit_sub_status IN ('APPROVED', 'AUTO_APPROVED') " +
      "AND ss.slot_start >= NOW()  " +
      " ORDER BY ss.slot_start, v.id",
    nativeQuery = true,
  )
  fun getFutureVisitNotificationEvents(@Param("prisonCode") prisonCode: String): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v ON v.id  = vne.visit_id " +
      " JOIN prison p on p.id  = v.prison_id  AND p.code= :prisonCode " +
      " JOIN session_slot ss on ss.id  = v.session_slot_id " +
      " WHERE v.visit_status = 'BOOKED' AND ss.slot_start >= NOW()  " +
      "AND v.visit_sub_status IN ('APPROVED', 'AUTO_APPROVED') " +
      "  AND vne.type IN (:notificationEventTypes) " +
      " ORDER BY ss.slot_start, v.id",
    nativeQuery = true,
  )
  fun getFutureVisitNotificationEvents(@Param("prisonCode") prisonCode: String, notificationEventTypes: List<String>): List<VisitNotificationEvent>

  @Query(
    "select vne.* FROM visit_notification_event vne " +
      "JOIN visit_notification_event_attribute vnea on vne.id = vnea.visit_notification_event_id " +
      "WHERE vnea.attribute_name = 'PAIRED_VISIT' " +
      "AND vnea.attribute_value = :visitReference",
    nativeQuery = true,
  )
  fun getPairedVisitNotificationEvents(visitReference: String): List<VisitNotificationEvent>

  @Query(
    "SELECT vne.type FROM visit_notification_event vne JOIN visit v " +
      "ON vne.visit_id = v.id " +
      "WHERE v.reference=:bookingReference GROUP by vne.type order by vne.type",
    nativeQuery = true,
  )
  fun getNotificationsTypesForBookingReference(@Param("bookingReference") bookingReference: String): List<NotificationEventType>

  @Query(
    "select vne.* FROM visit_notification_event vne " +
      "JOIN visit_notification_event_attribute vnea on vne.id = vnea.visit_notification_event_id " +
      "WHERE vnea.attribute_name = 'APPOINTMENT_INSTANCE_ID' " +
      "AND vnea.attribute_value = :appointmentInstanceId",
    nativeQuery = true,
  )
  fun getCourtAppointmentCreatedVisitNotificationEvents(appointmentInstanceId: String): List<VisitNotificationEvent>

  fun findVisitNotificationEventByVisitReference(
    reference: String,
  ): List<VisitNotificationEvent>

  fun deleteVisitNotificationEventByVisitReference(reference: String): Int

  fun deleteByReference(@Param("reference") reference: String): Int
}
