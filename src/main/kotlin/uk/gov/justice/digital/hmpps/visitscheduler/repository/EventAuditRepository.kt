package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.ApplicationMethodType
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.ActionedBy
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.EventAudit
import java.time.LocalDateTime

@Repository
interface EventAuditRepository : JpaRepository<EventAudit, Long> {
  @Query(
    "select ea from EventAudit ea left join fetch ea.eventAuditAttributes",
  )
  override fun findAll(): List<EventAudit>

  @Query(
    "select ea from EventAudit ea left join fetch ea.eventAuditAttributes " +
      "where ea.bookingReference = :bookingReference",
  )
  fun findByBookingReferenceOrderById(bookingReference: String): List<EventAudit>

  @Query(
    "SELECT ea FROM EventAudit ea left join fetch ea.eventAuditAttributes " +
      "WHERE ea.bookingReference = :bookingReference AND (ea.type = 'BOOKED_VISIT' or ea.type = 'REQUESTED_VISIT') " +
      "ORDER BY ea.id DESC LIMIT 1 ",
  )
  fun findLastBookedVisitEventByBookingReference(bookingReference: String): EventAudit?

  @Query(
    "SELECT ea FROM EventAudit ea left join fetch ea.eventAuditAttributes " +
      "WHERE ea.bookingReference = :bookingReference AND (ea.type = 'BOOKED_VISIT' or ea.type = 'MIGRATED_VISIT' or ea.type = 'REQUESTED_VISIT') " +
      "ORDER BY ea.createTimestamp DESC LIMIT 1 ",
  )
  fun findLastBookedOrMigratedVisitEventByBookingReference(bookingReference: String): EventAudit?

  @Query(
    "SELECT ea.actionedBy FROM EventAudit ea " +
      " WHERE ea.bookingReference = :bookingReference AND ea.type in (uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.BOOKED_VISIT, uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.MIGRATED_VISIT, uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.EventAuditType.REQUESTED_VISIT) " +
      "ORDER BY ea.id DESC LIMIT 1 ",
  )
  fun getLastBookedOrMigratedUser(bookingReference: String): ActionedBy?

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

  @Query(
    "SELECT ea FROM EventAudit ea left join fetch ea.eventAuditAttributes " +
      " where ea.actionedBy.bookerReference = :bookerReference" +
      " and ea.type NOT IN (:ignoreEventTypes)",
  )
  fun getVisitEventsByBookingReference(bookerReference: String, ignoreEventTypes: List<EventAuditType>): List<EventAudit>
}
