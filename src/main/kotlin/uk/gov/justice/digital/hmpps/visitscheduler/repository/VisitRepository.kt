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
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDateTime

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Query(
    "SELECT v.applicationReference FROM Visit v " +
      "WHERE (v.visitStatus = 'RESERVED' OR v.visitStatus = 'CHANGING')" +
      " AND v.modifyTimestamp < :expiredDateAndTime",
  )
  fun findExpiredApplicationReferences(expiredDateAndTime: LocalDateTime): List<String>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByApplicationReferenceInAndVisitStatusIn(applicationReference: List<String>, status: List<VisitStatus>)

  @Query(
    "SELECT * FROM visit " +
      "WHERE reference = :reference AND visit_status IN ('BOOKED','CANCELLED')  " +
      "ORDER BY modify_timestamp DESC LIMIT 1 ",
    nativeQuery = true,
  )
  fun findByReference(reference: String): Visit?

  @Query(
    "SELECT * FROM visit " +
      "WHERE reference = :reference AND visit_status IN ('BOOKED','CANCELLED')  " +
      "ORDER BY id",
    nativeQuery = true,
  )
  fun findAllByReference(reference: String): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE v.applicationReference = :applicationReference AND (v.visitStatus = 'CHANGING' OR v.visitStatus = 'RESERVED') ",
  )
  fun findApplication(applicationReference: String): Visit?

  @Query(
    "SELECT v FROM Visit v WHERE v.applicationReference = :applicationReference AND v.visitStatus = 'BOOKED' ",
  )
  fun findBookedApplication(applicationReference: String): Visit?

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v WHERE v.applicationReference = :applicationReference AND (v.visitStatus = 'BOOKED') ",
  )
  fun isApplicationBooked(applicationReference: String): Boolean

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v WHERE v.reference = :reference AND (v.visitStatus = 'CANCELLED') AND (v.outcomeStatus != 'SUPERSEDED_CANCELLATION')",
  )
  fun isBookingCancelled(reference: String): Boolean

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.prisonerId IN (:prisonerIds) AND " +
      "v.prison.code = :prisonCode AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      "(v.visitStatus = 'BOOKED' OR v.visitStatus = 'RESERVED') ",
  )
  fun hasActiveVisits(
    prisonerIds: List<String>,
    prisonCode: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
  ): Boolean

  @Query(
    "SELECT v.visitRestriction AS visitRestriction, COUNT(v) AS count  FROM Visit v " +
      "WHERE v.prison.code = :prisonCode AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      "(:capacityGroup is null or v.capacityGroup = :capacityGroup) AND " +
      "(v.visitRestriction = 'OPEN' OR v.visitRestriction = 'CLOSED') AND " +
      "v.visitStatus = 'BOOKED'  " +
      "GROUP BY v.visitRestriction",
  )
  fun getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
    prisonCode: String,
    capacityGroup: String ? = null,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v.visitRestriction AS visitRestriction, COUNT(v) AS count  FROM Visit v " +
      "WHERE v.prison.code = :prisonCode AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      "(:capacityGroup is null or v.capacityGroup = :capacityGroup) AND " +
      "(v.visitRestriction = 'OPEN' OR v.visitRestriction = 'CLOSED') AND " +
      "v.visitStatus = 'RESERVED' AND v.modifyTimestamp >= :expiredDateAndTime " +
      "GROUP BY v.visitRestriction",
  )
  fun getCountOfReservedSessionVisitsForOpenOrClosedRestriction(
    prisonCode: String,
    capacityGroup: String ? = null,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    expiredDateAndTime: LocalDateTime,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'BOOKED' ",
  )
  fun findBookedVisit(reference: String): Visit?

  @Transactional
  @Modifying
  @Query(
    "Update visit SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int

  @Transactional
  @Modifying
  @Query(
    "Update visit SET  create_timestamp = :createTimestamp WHERE id=:id",
    nativeQuery = true,
  )
  fun updateCreateTimestamp(createTimestamp: LocalDateTime, id: Long): Int

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v LEFT JOIN v.visitors as vis " +
      "WHERE (v.visitStatus = 'BOOKED' OR v.visitStatus = 'RESERVED')  AND " +
      "(v.prisonerId = :prisonerId) AND " +
      "(v.prison.code = :prisonCode) AND " +
      "(v.visitStart >= :startDateTime) AND " +
      "(cast(:endDateTime as date) is null OR v.visitStart < :endDateTime) ",
  )
  fun hasVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonCode") prisonCode: String,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
  ): Boolean
}
