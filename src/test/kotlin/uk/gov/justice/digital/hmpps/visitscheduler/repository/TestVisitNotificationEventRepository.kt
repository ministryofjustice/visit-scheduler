package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.notification.VisitNotificationEvent

@Repository
interface TestVisitNotificationEventRepository : JpaRepository<VisitNotificationEvent, Int> {
  @Query(
    "SELECT vne.* FROM visit_notification_event vne " +
      " JOIN visit v ON v.reference  = vne.booking_reference AND v.visit_status = 'BOOKED'  " +
      " JOIN session_slot ss ON ss.id  = v.session_slot_id " +
      " JOIN prison p on p.id  = v.prison_id  AND p.code= :prisonCode " +
      " ORDER BY ss.slot_date, ss.slot_start, vne.reference",
    nativeQuery = true,
  )
  fun getFutureVisitNotificationEvents(@Param("prisonCode") prisonCode: String): List<VisitNotificationEvent>

  fun findByBookingReference(@Param("bookingReference") bookingReference: String): List<VisitNotificationEvent>

  @Query("SELECT vne FROM VisitNotificationEvent vne  ORDER BY vne.id")
  fun findAllOrderById(): List<VisitNotificationEvent>
}
