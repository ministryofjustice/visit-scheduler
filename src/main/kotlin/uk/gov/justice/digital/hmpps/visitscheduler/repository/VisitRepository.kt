package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

  @Query(
    "SELECT * FROM visit " +
      "WHERE reference = :reference AND visit_status IN ('BOOKED','CANCELLED')  " +
      "ORDER BY modify_timestamp DESC LIMIT 1 ",
    nativeQuery = true
  )
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
    "SELECT v FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'RESERVED' "
  )
  fun findReservedVisit(reference: String): Visit?

  @Transactional
  @Modifying
  @Query(
    "Update visit SET  modify_timestamp = :modifyTimestamp WHERE id=:id",
    nativeQuery = true
  )
  fun updateModifyTimestamp(modifyTimestamp: LocalDateTime, id: Long): Int

  @Transactional
  @Modifying
  @Query(
    "Update visit SET  create_timestamp = :createTimestamp WHERE id=:id",
    nativeQuery = true
  )
  fun updateCreateTimestamp(createTimestamp: LocalDateTime, id: Long): Int

  @Query(
    "SELECT v FROM Visit v LEFT JOIN v.visitors as vis " +
      "WHERE (:visitStatus is null OR v.visitStatus = :visitStatus)  AND " +
      "(:nomisPersonId is null OR vis.nomisPersonId = :nomisPersonId) AND " +
      "(:prisonerId is null OR v.prisonerId = :prisonerId) AND " +
      "(:prisonId is null OR v.prisonId = :prisonId) AND " +
      "(cast(:startDateTime as date) is null OR v.visitStart >= :startDateTime) AND " +
      "(cast(:endDateTime as date) is null OR v.visitStart < :endDateTime) "
  )
  fun findPageBy(
    @Param("prisonerId") prisonerId: String? = null,
    @Param("prisonId") prisonId: String,
    @Param("startDateTime") startDateTime: LocalDateTime? = null,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
    @Param("visitStatus") visitStatus: VisitStatus? = null,
    @Param("nomisPersonId") nomisPersonId: Long? = null,
    page: Pageable
  ): Page<Visit>

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v LEFT JOIN v.visitors as vis " +
      "WHERE (v.visitStatus in :visitStatus)  AND " +
      "(v.prisonerId = :prisonerId) AND " +
      "(v.prisonId = :prisonId) AND " +
      "(v.visitStart >= :startDateTime) AND " +
      "(cast(:endDateTime as date) is null OR v.visitStart < :endDateTime) "
  )
  fun hasVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonId") prisonId: String,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
    @Param("visitStatus") visitStatus: List<VisitStatus>
  ): Boolean
}
