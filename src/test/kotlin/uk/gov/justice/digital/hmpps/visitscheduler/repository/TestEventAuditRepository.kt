package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit

@Repository
interface TestEventAuditRepository : JpaRepository<EventAudit, Long> {
  fun findAllByBookingReference(bookingReference: String): List<EventAudit>

  @Query(
    "SELECT * FROM event_audit " +
      "WHERE booking_reference = :bookingReference " +
      "ORDER BY id DESC LIMIT 1 ",
    nativeQuery = true,
  )
  fun findLastEventByBookingReference(bookingReference: String): EventAudit

  @Query(
    "SELECT count(*) FROM event_audit " +
      "WHERE type = :type",
    nativeQuery = true,
  )
  fun getAuditCount(type: String): Int

  @Query(
    "SELECT * FROM event_audit " +
      "WHERE type = :type",
    nativeQuery = true,
  )
  fun getAuditByType(type: String): List<EventAudit>

  @Query(
    "SELECT * FROM event_audit " +
      "WHERE booking_reference = :bookingReference AND type = 'BOOKED_VISIT'  " +
      "ORDER BY id DESC LIMIT 1 ",
    nativeQuery = true,
  )
  fun findLastBookedVisitEventByBookingReference(bookingReference: String): EventAudit
}
