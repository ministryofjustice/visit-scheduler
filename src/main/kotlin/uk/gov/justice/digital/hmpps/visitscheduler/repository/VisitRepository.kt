package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.model.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Repository
interface VisitRepository : JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

  @Query(
    "SELECT * FROM visit " +
      "WHERE reference = :reference ",
    nativeQuery = true,
  )
  fun findByReference(reference: String): Visit?

  @Query(
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'CANCELLED'",
  )
  fun isBookingCancelled(reference: String): Boolean

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.prisonerId IN (:prisonerIds) AND " +
      "v.prison.code = :prisonCode AND " +
      "v.sessionSlot.slotDate = :slotDate AND " +
      "v.visitStatus = 'BOOKED'",
  )
  fun hasActiveVisitsForDate(
    prisonerIds: List<String>,
    prisonCode: String,
    slotDate: LocalDate,
  ): Boolean

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.sessionSlot.sessionTemplateReference = :sessionTemplateReference AND " +
      "(cast(:slotDate as date)  is null OR v.sessionSlot.slotDate >= :slotDate) ",
  )
  fun hasVisitsForSessionTemplate(
    sessionTemplateReference: String,
    slotDate: LocalDate? = null,
  ): Boolean

  @Query(
    "SELECT count(v) > 0 FROM Visit v " +
      "WHERE v.sessionSlot.sessionTemplateReference = :sessionTemplateReference AND " +
      "(cast(:slotDate as date)  is null OR v.sessionSlot.slotDate >= :slotDate) AND " +
      "v.visitStatus = 'BOOKED'",
  )
  fun hasBookedVisitsForSessionTemplate(
    sessionTemplateReference: String,
    slotDate: LocalDate? = null,
  ): Boolean

  @Query(
    "SELECT v.visit_restriction AS visitRestriction, COUNT(*) AS count  FROM visit v " +
      "JOIN session_slot ss ON ss.id = v.session_slot_id " +
      "WHERE ss.session_template_reference = :sessionTemplateReference AND " +
      "ss.slot_date = :slotDate AND " +
      "v.visit_restriction in ('OPEN','CLOSED') AND " +
      "v.visit_status = 'BOOKED' " +
      "GROUP BY v.visit_restriction",
    nativeQuery = true,
  )
  fun getCountOfBookedSessionVisitsForOpenOrClosedRestriction(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v.visit_restriction AS visitRestriction, COUNT(*) AS count  FROM visit v " +
      "JOIN session_slot ss ON ss.id = v.session_slot_id " +
      "WHERE ss.session_template_reference = :sessionTemplateReference AND " +
      "(ss.slot_date >= :slotDate AND ss.slot_date < (CAST(:slotDate AS DATE) + CAST('1 day' AS INTERVAL))) AND " +
      "v.visit_restriction in ('OPEN','CLOSED') AND " +
      "v.visit_status = 'CANCELLED' " +
      "GROUP BY v.visit_restriction",
    nativeQuery = true,
  )
  fun getCountOfCancelledSessionVisitsForOpenOrClosedRestriction(
    sessionTemplateReference: String,
    slotDate: LocalDate,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'BOOKED' ",
  )
  fun findBookedVisit(reference: String): Visit?

  @Query(
    "SELECT v.*  FROM visit v" +
      "  JOIN visits_to_applications vta ON vta.visit_id = v.id " +
      "  JOIN application a on a.id = vta.application_id " +
      "  WHERE a.reference = :applicationReference",
    nativeQuery = true,
  )
  fun findVisitByApplicationReference(applicationReference: String): Visit?

  @Query(
    "SELECT COUNT(*) > 0  FROM visit v" +
      "  JOIN application a on a.visit_id = v.id " +
      "  WHERE a.reference = :applicationReference AND v.visit_status = 'BOOKED'",
    nativeQuery = true,
  )
  fun doesBookedVisitExist(applicationReference: String): Boolean

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
    "SELECT CASE WHEN (COUNT(v) > 0) THEN TRUE ELSE FALSE END FROM Visit v " +
      "WHERE v.visitStatus = 'BOOKED'  AND " +
      "(v.prisonerId = :prisonerId) AND " +
      "(v.sessionSlot.sessionTemplateReference = :sessionTemplateReference) AND " +
      "v.sessionSlot.slotDate = :slotDate ",
  )
  fun hasActiveVisitForDate(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionTemplateReference") sessionTemplateReference: String,
    @Param("slotDate") slotDate: LocalDate,
  ): Boolean

  @Query(
    "SELECT v  FROM Visit v " +
      "WHERE v.visitStatus = 'BOOKED' AND " +
      "(v.prisonerId = :prisonerId) AND " +
      "(:prisonCode is null or v.prison.code = :prisonCode) AND " +
      "(v.sessionSlot.slotDate >= :#{#startDateTime.toLocalDate()} AND v.sessionSlot.slotTime >= :#{#startDateTime.toLocalTime()}) AND " +
      "(:#{#endDateTime} is null OR (v.sessionSlot.slotDate <= :#{#endDateTime.toLocalDate()} AND v.sessionSlot.slotEndTime < :#{#endDateTime.toLocalTime()})) ",
  )
  fun getVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonCode") prisonCode: String? = null,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v JOIN v.visitors vis WHERE " +
      "(:#{#prisonerId} is null OR v.prisonerId = :prisonerId)  AND  " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(:#{#visitorId} is null OR vis.nomisPersonId = :visitorId) AND " +
      "(CAST(:slotStartDate AS DATE) is null OR v.sessionSlot.slotDate >= :slotStartDate) AND " +
      "(CAST(:slotStartTime AS TIME) is null OR v.sessionSlot.slotTime >= :slotStartTime) AND " +
      "(CAST(:slotEndDate AS DATE) is null OR v.sessionSlot.slotDate <= :slotEndDate) AND " +
      "(CAST(:slotEndTime AS TIME) is null OR v.sessionSlot.slotEndTime <= :slotEndTime) " +
      " ORDER BY v.sessionSlot.slotDate,v.sessionSlot.slotTime",
  )
  fun findVisitsOrderByDateAndTime(
    prisonerId: String?,
    prisonCode: String?,
    visitStatusList: List<VisitStatus>?,
    visitorId: Long?,
    slotStartDate: LocalDate?,
    slotStartTime: LocalTime?,
    slotEndDate: LocalDate?,
    slotEndTime: LocalTime?,
    pageable: Pageable,
  ): Page<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      "(:#{#prisonerId} is null OR v.prisonerId = :prisonerId)  AND  " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(CAST(:slotStartDate AS DATE) is null OR v.sessionSlot.slotDate >= :slotStartDate) AND " +
      "(CAST(:slotStartTime AS TIME) is null OR v.sessionSlot.slotTime >= :slotStartTime) AND " +
      "(CAST(:slotEndDate AS DATE) is null OR v.sessionSlot.slotDate <= :slotEndDate) AND " +
      "(CAST(:slotEndTime AS TIME) is null OR v.sessionSlot.slotEndTime <= :slotEndTime) " +
      " ORDER BY v.sessionSlot.slotDate,v.sessionSlot.slotTime",
  )
  fun findVisits(
    prisonerId: String?,
    prisonCode: String?,
    visitStatusList: List<VisitStatus>?,
    slotStartDate: LocalDate?,
    slotStartTime: LocalTime?,
    slotEndDate: LocalDate?,
    slotEndTime: LocalTime?,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " v.sessionSlot.sessionTemplateReference = :sessionTemplateReference  AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(:#{#visitRestrictions} is null OR v.visitRestriction in :visitRestrictions) AND " +
      "v.sessionSlot.slotDate >= :fromDate  AND " +
      "v.sessionSlot.slotDate <= :toDate " +
      " ORDER BY v.createTimestamp",
  )
  fun findVisitsOrderByCreateTimestamp(
    sessionTemplateReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatusList: List<VisitStatus>?,
    visitRestrictions: List<VisitRestriction>?,
    pageable: Pageable,
  ): Page<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " v.visitStatus = 'BOOKED'  AND " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(CAST(:date AS DATE) is null OR v.sessionSlot.slotDate = :date) " +
      " ORDER BY v.sessionSlot.slotDate,v.sessionSlot.slotTime",
  )
  fun findBookedVisitsForDate(prisonCode: String, date: LocalDate): List<Visit>
}
