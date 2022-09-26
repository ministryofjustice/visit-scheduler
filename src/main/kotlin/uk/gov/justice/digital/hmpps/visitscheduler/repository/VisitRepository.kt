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
    "SELECT v.application_reference FROM visit v " +
      "WHERE v.visit_status = 'RESERVED' and v.modify_timestamp < NOW() - (make_interval(mins => :expiredPeriodMinutes))",
    nativeQuery = true
  )
  fun findExpiredApplicationReferences(expiredPeriodMinutes: Int): List<String>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByApplicationReferenceInAndVisitStatus(applicationReference: List<String>, status: VisitStatus)

  fun findByReference(reference: String): Visit?

  fun findByApplicationReference(applicationReference: String): Visit?

  fun findAllByReference(reference: String): List<Visit>

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.prisonerId in (:prisonerIds) and " +
      "v.prisonId = :prisonId and " +
      "v.visitStart >= :startDateTime and " +
      "v.visitStart < :endDateTime and " +
      " (v.visitStatus = 'BOOKED' or v.visitStatus = 'RESERVED') "
  )
  fun hasActiveVisits(
    prisonerIds: List<String>,
    prisonId: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): Boolean

  @Query(
    "SELECT v.visitRestriction as visitRestriction, count(v) as count  FROM Visit v " +
      "WHERE v.prisonId = :prisonId and " +
      "v.visitStart >= :startDateTime and " +
      "v.visitStart < :endDateTime and " +
      "v.visitRoom = :visitRoom and " +
      "(v.visitRestriction = 'OPEN' or v.visitRestriction = 'CLOSED') and " +
      "v.visitStatus = 'BOOKED'  " +
      "group by v.visitRestriction"
  )
  fun getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
    prisonId: String,
    visitRoom: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v.visit_restriction as visitRestriction, count(*) as count  FROM visit v " +
      "WHERE v.prison_id = :prisonId and " +
      "v.visit_start >= :startDateTime and " +
      "v.visit_start < :endDateTime and " +
      "v.visit_room = :visitRoom and " +
      "v.visit_restriction in ('OPEN','CLOSED') and " +
      "v.visit_status = 'RESERVED' and v.modify_timestamp >= NOW() - (make_interval(mins => :expiredPeriodMinutes))" +
      "group by v.visit_restriction",
    nativeQuery = true
  )
  fun getCountOfReservedSessionVisitsForOpenOrClosedRestriction(
    prisonId: String,
    visitRoom: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime,
    expiredPeriodMinutes: Int
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference and v.visitStatus = 'BOOKED' "
  )
  fun findBookedVisit(reference: String): Visit?
}
