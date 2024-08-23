package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import java.time.LocalDateTime

@Repository
interface EventAuditRepository : JpaRepository<EventAudit, Long> {
  fun findByBookingReferenceOrderById(bookingReference: String): List<EventAudit>

  @Query(
    "SELECT * FROM event_audit " +
      "WHERE booking_reference = :bookingReference AND type = 'BOOKED_VISIT'  " +
      "ORDER BY id DESC LIMIT 1 ",
    nativeQuery = true,
  )
  fun findLastBookedVisitEventByBookingReference(bookingReference: String): EventAudit?

  @Query(
    "SELECT ea.actionedBy FROM EventAudit ea " +
      " WHERE ea.bookingReference = :bookingReference AND ea.type in (uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.UPDATED_VISIT,uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT, EventAuditType.UPDATED_VISIT,uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.MIGRATED_VISIT) " +
      "ORDER BY ea.id DESC LIMIT 1 ",
  )
  fun getLastUserToUpdateBookingByReference(bookingReference: String): ActionedBy

  @Transactional
  @Modifying
  @Query(
    "Update event_audit SET application_method_type=:#{#applicationMethodType.name()},  " +
      " booking_reference= :bookingReference " +
      "WHERE application_reference = :applicationReference AND type in ('RESERVED_VISIT','CHANGING_VISIT')",
    nativeQuery = true,
  )
  fun updateVisitApplication(applicationReference: String, bookingReference: String, applicationMethodType: ApplicationMethodType): Int?

  @Transactional
  @Modifying
  @Query(
    "Update event_audit SET  create_timestamp = :createTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateCreateTimestamp(createTimestamp: LocalDateTime, id: Long): Int
}
