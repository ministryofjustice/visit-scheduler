package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
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
    "SELECT a FROM Application a " +
      "WHERE a.completed = false" +
      " AND a.modifyTimestamp < :expiredDateAndTime ORDER BY a.id",
  )
  fun findExpiredApplicationReferences(expiredDateAndTime: LocalDateTime): List<Application>

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
      "JOIN session_slot ss ON ss.id = a.session_slot_id " +
      "WHERE ss.session_template_reference = :sessionTemplateReference AND " +
      "ss.slot_date = :slotDate AND " +
      "a.restriction IN ('OPEN','CLOSED') AND " +
      "a.reserved_slot = true AND a.completed = false " +
      "GROUP BY a.restriction",
    nativeQuery = true,
  )
  fun getCountOfReservedSessionForOpenOrClosedRestriction(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT COUNT(a) > 0 FROM Application a " +
      "WHERE a.completed = false AND a.reservedSlot = true AND " +
      "(a.prisonerId = :prisonerId) AND " +
      "(a.sessionSlot.sessionTemplateReference = :sessionTemplateReference) AND " +
      "(a.sessionSlot.slotDate = :slotDate) ",
  )
  fun hasReservations(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionTemplateReference") sessionTemplateReference: String,
    @Param("slotDate") slotDate: LocalDate,
  ): Boolean

  @Query(
    "SELECT count(a) > 0 FROM Application a " +
      "WHERE a.prisonerId IN (:prisonerIds) AND " +
      "a.prison.code = :prisonCode AND " +
      "a.sessionSlot.slotDate = :slotDate AND " +
      "a.completed = false AND  a.reservedSlot = true",
  )
  fun hasActiveApplicationsForDate(
    prisonerIds: List<String>,
    prisonCode: String,
    slotDate: LocalDate,
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
