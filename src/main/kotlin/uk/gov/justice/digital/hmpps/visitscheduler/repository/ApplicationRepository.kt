package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface ApplicationRepository : JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

  @Query(
    "SELECT a FROM Application a " +
      "WHERE a.completed = false" +
      " AND a.modifyTimestamp < :expiredDateAndTime ORDER BY a.id",
  )
  fun findApplicationByModifyTimes(expiredDateAndTime: LocalDateTime): List<Application>

  @Query(
    "SELECT a FROM Application a WHERE a.reference = :applicationReference",
  )
  fun findApplication(applicationReference: String): Application?

  @Query(
    "SELECT count(a)>0 FROM Application a WHERE a.completed = true AND a.reference = :applicationReference",
  )
  fun isApplicationCompleted(applicationReference: String): Boolean

  @Query(
    "SELECT a.restriction AS visitRestriction, COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction IN ('OPEN','CLOSED') AND " +
      " a.reserved_slot = true AND a.completed = false AND" +
      " a.modify_timestamp >= :expiredDateAndTime " +
      " GROUP BY a.restriction",
    nativeQuery = true,
  )
  fun getCountOfReservedSessionForOpenOrClosedRestriction(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction = 'CLOSED' AND " +
      " a.reserved_slot = true AND a.completed = false AND" +
      " a.modify_timestamp >= :expiredDateAndTime ",
    nativeQuery = true,
  )
  fun getCountOfReservedApplicationsForClosedSessionSlot(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): Long

  @Query(
    "SELECT COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction = 'OPEN' AND " +
      " a.reserved_slot = true AND a.completed = false AND" +
      " a.modify_timestamp >= :expiredDateAndTime ",
    nativeQuery = true,
  )
  fun getCountOfReservedApplicationsForOpenSessionSlot(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): Long

  @Query(
    "SELECT COUNT(a) > 0 FROM Application a " +
      "WHERE a.completed = false AND a.reservedSlot = true AND " +
      "a.prisonerId = :prisonerId AND " +
      "a.modifyTimestamp >= :expiredDateAndTime AND " +
      "a.sessionSlotId = :sessionSlotId ",
  )
  fun hasReservations(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionSlotId") sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): Boolean

  @Query(
    "SELECT count(*) > 0 FROM application a left join session_slot sl on a.session_slot_id = sl.id " +
      "WHERE a.prisoner_id IN :prisonerIds AND " +
      "sl.slot_date = :sessionDate AND " +
      "a.modify_timestamp >= :expiredDateAndTime AND " +
      "a.completed = false AND a.reserved_slot = true",
    nativeQuery = true,
  )
  fun hasActiveApplicationsForDate(
    prisonerIds: List<String>,
    sessionDate: LocalDate,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): Boolean
}
