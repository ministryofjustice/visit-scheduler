package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDateTime
import javax.persistence.LockModeType

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  fun deleteAllByReferenceIn(reference: List<String>)

  fun findByReference(reference: String): Visit?

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
      " (v.visitStatus = 'BOOKED' or v.visitStatus = 'RESERVED') " +
      "group by v.visitRestriction"
  )
  fun getCountOfActiveSessionVisitsForOpenOrClosedRestriction(
    prisonId: String,
    visitRoom: String,
    startDateTime: LocalDateTime,
    endDateTime: LocalDateTime
  ): List<VisitRestrictionStats>

  @Query(
    "Update Visit v set v.visitStatus = 'CANCELLED' WHERE v.reference = :reference and v.visitStatus = 'BOOKED'  "
  )
  fun cancelBookedVisit(reference: String): Visit?

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference and v.visitStatus = 'RESERVED' "
  )
  fun findReservedVisit(reference: String): Visit?

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference and v.visitStatus = 'BOOKED' "
  )
  fun findBookedVisit(reference: String): Visit?

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference and v.active = true "
  )
  fun findActiveVisit(reference: String): Visit?
}
