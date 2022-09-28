package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDateTime
import javax.persistence.LockModeType

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Query(
    "SELECT v.applicationReference FROM Visit v " +
      "WHERE (v.visitStatus = 'RESERVED' OR v.visitStatus = 'CHANGING')" +
      " AND v.modifyTimestamp < :expiredDateAndTime"
  )
  fun findExpiredApplicationReferences(expiredDateAndTime: LocalDateTime): List<String>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByApplicationReferenceInAndVisitStatusIn(applicationReference: List<String>, status: List<VisitStatus>)

  fun findByReference(reference: String): Visit?

  fun findByApplicationReference(applicationReference: String): Visit?

  fun findAllByReference(reference: String): List<Visit>

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.prisonerId IN (:prisonerIds) AND " +
      "v.prisonId = :prisonId AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      " (v.visitStatus = 'BOOKED' OR v.visitStatus = 'RESERVED') "
  )
  fun hasActiveVisits(
    prisonerIds: List<String>,
    prisonId: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): Boolean

  @Query(
    "SELECT v.visitRestriction AS visitRestriction, COUNT(v) AS count  FROM Visit v " +
      "WHERE v.prisonId = :prisonId AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      "v.visitRoom = :visitRoom AND " +
      "(v.visitRestriction = 'OPEN' OR v.visitRestriction = 'CLOSED') AND " +
      "v.visitStatus = 'BOOKED'  " +
      "GROUP BY v.visitRestriction"
  )
  fun getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
    prisonId: String,
    visitRoom: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v.visitRestriction AS visitRestriction, COUNT(v) AS count  FROM Visit v " +
      "WHERE v.prisonId = :prisonId AND " +
      "v.visitStart >= :startDateTime AND " +
      "v.visitStart < :endDateTime AND " +
      "v.visitRoom = :visitRoom AND " +
      "(v.visitRestriction = 'OPEN' OR v.visitRestriction = 'CLOSED') AND " +
      "v.visitStatus = 'RESERVED' AND v.modifyTimestamp >= :expiredDateAndTime " +
      "GROUP BY v.visitRestriction"
  )
  fun getCountOfReservedSessionVisitsForOpenOrClosedRestriction(
    prisonId: String,
    visitRoom: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    expiredDateAndTime: LocalDateTime
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'BOOKED' "
  )
  fun findBookedVisit(reference: String): Visit?

  @Query(
    "SELECT CASE WHEN (COUNT(v) = 1) THEN TRUE ELSE FALSE END  FROM Visit v WHERE v.reference = :bookingReference AND v.visitStatus = 'BOOKED' "
  )
  fun isValidBookingReference(bookingReference: String): Boolean
}
