package uk.gov.justice.digital.hmpps.visitscheduler.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface ApplicationRepository : JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

  @Query(
    "Select * FROM application " +
      "WHERE completed = false AND modifyTimestamp < now()-make_interval(minute => :expiredPeriodMinutes) ",
    nativeQuery = true,
  )
  fun findExpiredApplicationReferences(expiredPeriodMinutes: Int): List<String>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
    "Delete FROM application " +
      "WHERE completed = false AND " +
      " modifyTimestamp < now()-make_interval(minute => :expiredPeriodMinutes) AND " +
      " reference = :applicationReference ",
    nativeQuery = true,
  )
  fun deleteExpiredApplications(applicationReference: String, expiredPeriodMinutes: Int): Int

  @Query(
    "SELECT a FROM Application a WHERE a.reference = :applicationReference",
  )
  fun findApplication(applicationReference: String): Application?

  @Query(
    "SELECT a.completed FROM Application a WHERE a.reference = :applicationReference",
  )
  fun isApplicationCompleted(applicationReference: String): Boolean

  @Query(
    "SELECT a.restriction AS visitRestriction, COUNT(v) AS count  FROM application a " +
      "JOIN session_slot ss ON ss.id = a.session_slot_id " +
      "WHERE ss.session_template_reference = :sessionTemplateReference AND " +
      "(ss.slotDate >= :sessionDate AND ss.slotDate < (CAST(:sessionDate AS DATE) + CAST('1 day' AS INTERVAL))) AND " +
      "a.restriction IN ('OPEN','CLOSED') AND " +
      "a.reserved_slot = true AND a.completed = false AND " +
      "a.modify_timestamp >= :expiredDateAndTime " +
      "GROUP BY a.restriction",
    nativeQuery = true,
  )
  fun getCountOfReservedSessionForOpenOrClosedRestriction(
    sessionTemplateReference: String,
    sessionDate: LocalDate,
    expiredDateAndTime: LocalDateTime,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT CASE WHEN (COUNT(a) > 0) THEN TRUE ELSE FALSE END FROM Application a " +
      "WHERE a.completed = false AND a.reservedSlot = true AND " +
      "(a.prisonerId = :prisonerId) AND " +
      "(a.sessionSlot.sessionTemplateReference = :sessionTemplateReference) AND " +
      "(a.sessionSlot.slotDate >= :startDateTime) ",
  )
  fun hasReservations(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionTemplateReference") sessionTemplateReference: String,
    @Param("slotDate") slotDate: LocalDate,
  ): Boolean

  @Transactional
  @Modifying
  @Query(
    "Update application SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int

  @Transactional
  @Modifying
  @Query(
    "Update application SET  create_timestamp = :createTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateCreateTimestamp(createTimestamp: LocalDateTime, id: Long): Int
}
