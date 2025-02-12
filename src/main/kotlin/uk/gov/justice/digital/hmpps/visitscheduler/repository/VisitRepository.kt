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
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitRestriction
import uk.gov.justice.digital.hmpps.visitscheduler.dto.enums.VisitStatus
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.Visit
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface VisitRepository :
  JpaRepository<Visit, Long>,
  JpaSpecificationExecutor<Visit> {

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
    "SELECT count(*) > 0 FROM visit v left join session_slot sl on v.session_slot_id = sl.id " +
      "WHERE v.prisoner_id IN :prisonerIds AND " +
      "v.prison_id = :prisonId AND " +
      "sl.slot_date = :sessionDate AND " +
      "v.visit_status = 'BOOKED'",
    nativeQuery = true,
  )
  fun hasActiveVisitsForDate(
    prisonerIds: List<String>,
    sessionDate: LocalDate,
    prisonId: Long,
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
      "WHERE v.session_slot_id = :sessionSlotId AND " +
      "v.visit_restriction in ('OPEN','CLOSED') AND " +
      "v.visit_status = 'BOOKED' " +
      "GROUP BY v.visit_restriction",
    nativeQuery = true,
  )
  fun getCountOfBookedSessionVisitsForOpenOrClosedRestriction(sessionSlotId: Long): List<VisitRestrictionStats>

  @Query(
    "SELECT COUNT(*) AS count  FROM visit v " +
      "WHERE v.session_slot_id = :sessionSlotId AND " +
      "v.visit_restriction = 'OPEN' AND " +
      "v.visit_status = 'BOOKED' ",
    nativeQuery = true,
  )
  fun getCountOfBookedForOpenSessionSlot(sessionSlotId: Long): Long

  @Query(
    "SELECT COUNT(*) AS count  FROM visit v " +
      "WHERE v.session_slot_id = :sessionSlotId AND " +
      "v.visit_restriction = 'CLOSED' AND " +
      "v.visit_status = 'BOOKED' ",
    nativeQuery = true,
  )
  fun getCountOfBookedForClosedSessionSlot(sessionSlotId: Long): Long

  @Query(
    "SELECT v.visit_restriction AS visitRestriction, COUNT(*) AS count  FROM visit v " +
      "WHERE v.session_slot_id = :sessionSlotId AND " +
      "v.visit_restriction in ('OPEN','CLOSED') AND " +
      "v.visit_status = 'CANCELLED' " +
      "GROUP BY v.visit_restriction",
    nativeQuery = true,
  )
  fun getCountOfCancelledSessionVisitsForOpenOrClosedRestriction(
    sessionSlotId: Long,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference = :reference AND v.visitStatus = 'BOOKED' ",
  )
  fun findBookedVisit(reference: String): Visit?

  @Query(
    "SELECT v.*  FROM visit v" +
      "  JOIN application a on a.visit_id = v.id " +
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
      "(v.sessionSlotId = :sessionSlotId) AND " +
      "(:#{#excludeVisitReference} is null OR v.reference != :excludeVisitReference)",
  )
  fun hasActiveVisitForSessionSlot(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionSlotId") sessionSlotId: Long,
    @Param("excludeVisitReference") excludeVisitReference: String? = null,
  ): Boolean

  @Query(
    "SELECT v  FROM Visit v " +
      "WHERE v.visitStatus = 'BOOKED' AND " +
      "v.prisonerId = :prisonerId AND " +
      "v.prison.code = :prisonCode AND " +
      "v.sessionSlot.slotDate >= :visitDate " +
      "ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun findBookedVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonCode") prisonCode: String,
    @Param("visitDate") visitDate: LocalDate,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      "(:#{#prisonerId} is null OR v.prisonerId = :prisonerId)  AND  " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(CAST(:visitStartDate AS DATE) is null OR v.sessionSlot.slotDate >= :visitStartDate) AND " +
      "(CAST(:visitEndDate AS DATE) is null OR v.sessionSlot.slotDate <= :visitEndDate) " +
      " ORDER BY v.sessionSlot.slotDate desc, v.sessionSlot.slotStart desc,v.id desc",
  )
  fun findVisitsOrderByDateAndTime(
    prisonerId: String?,
    prisonCode: String?,
    visitStatusList: List<VisitStatus>?,
    visitStartDate: LocalDate?,
    visitEndDate: LocalDate?,
    pageable: Pageable,
  ): Page<Visit>

  @Query(
    "SELECT v  FROM Visit v " +
      "WHERE v.sessionSlot.slotStart >= :startDateTime AND " +
      "v.prisonerId = :prisonerId AND " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(cast(:endDateTime as date) is null OR v.sessionSlot.slotEnd < :endDateTime) ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun getVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonCode") prisonCode: String?,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
  ): List<Visit>

  @Query(
    "SELECT v  FROM Visit v " +
      "WHERE v.sessionSlot.slotStart >= :startDateTime AND " +
      "v.prisonerId = :prisonerId AND " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "v.visitStatus = 'BOOKED' AND " +
      "(cast(:endDateTime as date) is null OR v.sessionSlot.slotEnd < :endDateTime) " +
      "ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun getBookedVisits(
    @Param("prisonerId") prisonerId: String,
    @Param("prisonCode") prisonCode: String?,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v " +
      "LEFT JOIN VisitVisitor vv ON v.id = vv.visitId " +
      "WHERE v.sessionSlot.slotStart >= :startDateTime AND " +
      "vv.nomisPersonId = :visitorId AND " +
      "v.visitStatus = 'BOOKED' AND " +
      "(:#{#prisonerId} is null OR v.prisonerId = :prisonerId) AND " +
      "(cast(:endDateTime as date) is null OR v.sessionSlot.slotEnd < :endDateTime) ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun getFutureVisitsByVisitorId(
    @Param("visitorId") visitorId: String,
    @Param("prisonerId") prisonerId: String?,
    @Param("startDateTime") startDateTime: LocalDateTime,
    @Param("endDateTime") endDateTime: LocalDateTime? = null,
  ): List<Visit>

  @Query(
    "SELECT v.* FROM visit v " +
      "LEFT JOIN prison p ON v.prison_id = p.id " +
      "LEFT JOIN session_slot sl ON v.session_slot_id = sl.id " +
      "WHERE v.prisoner_id = :prisonerId AND " +
      "v.visit_status = 'BOOKED' AND " +
      "p.code != :excludedPrisonCode AND " +
      "sl.slot_start >= NOW() ORDER BY v.id ",
    nativeQuery = true,
  )
  fun getFutureBookedVisitsExcludingPrison(
    @Param("prisonerId") prisonerId: String,
    @Param("excludedPrisonCode") excludedPrisonCode: String,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " (v.sessionSlot.sessionTemplateReference = :sessionTemplateReference)  AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(:#{#visitRestrictions} is null OR v.visitRestriction in :visitRestrictions) AND " +
      "v.sessionSlot.slotDate >= :fromDate  AND " +
      "v.sessionSlot.slotDate <= :toDate AND " +
      "v.prison.code = :prisonCode " +
      " ORDER BY v.createTimestamp",
  )
  fun findVisitsBySessionTemplateReference(
    sessionTemplateReference: String,
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatusList: List<VisitStatus>?,
    visitRestrictions: List<VisitRestriction>?,
    prisonCode: String,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " (v.sessionSlot.sessionTemplateReference = :sessionTemplateReference)  AND " +
      "v.sessionSlot.slotDate >= :fromDate AND " +
      "v.visitStatus = 'BOOKED' AND " +
      "(:#{#toDate} is null OR v.sessionSlot.slotDate <= :toDate) " +
      " ORDER BY v.createTimestamp",
  )
  fun findBookedVisitsBySessionTemplateReference(
    sessionTemplateReference: String,
    fromDate: LocalDate,
    toDate: LocalDate? = null,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " (v.sessionSlot.sessionTemplateReference is NULL)  AND " +
      "(:#{#visitStatusList} is null OR v.visitStatus in :visitStatusList) AND " +
      "(:#{#visitRestrictions} is null OR v.visitRestriction in :visitRestrictions) AND " +
      "v.sessionSlot.slotDate >= :fromDate  AND " +
      "v.sessionSlot.slotDate <= :toDate AND " +
      "v.prison.code = :prisonCode " +
      " ORDER BY v.createTimestamp",
  )
  fun findVisitsWithNoSessionTemplateReference(
    fromDate: LocalDate,
    toDate: LocalDate,
    visitStatusList: List<VisitStatus>?,
    visitRestrictions: List<VisitRestriction>?,
    prisonCode: String,
  ): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " v.visitStatus = 'BOOKED'  AND " +
      "(:#{#prisonCode} is null OR v.prison.code = :prisonCode) AND " +
      "(CAST(:date AS DATE) is null OR v.sessionSlot.slotDate = :date) " +
      " ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun findBookedVisitsForDate(prisonCode: String, date: LocalDate): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE " +
      " v.visitStatus = 'BOOKED'  AND " +
      "(v.sessionSlot.sessionTemplateReference = :sessionTemplateReference) AND " +
      "(v.sessionSlot.slotDate = :date) " +
      " ORDER BY v.sessionSlot.slotStart,v.id",
  )
  fun findBookedVisitsBySessionForDate(sessionTemplateReference: String, date: LocalDate): List<Visit>

  @Query(
    "SELECT v.* FROM visit v " +
      " INNER JOIN event_audit ea on ea.booking_reference = v.reference AND ea.type = 'BOOKED_VISIT' " +
      " INNER JOIN actioned_by ab on ab.id = ea.actioned_by_id" +
      " INNER JOIN session_slot ss on ss.id = v.session_slot_id " +
      " WHERE ab.booker_reference = :bookerReference " +
      " AND v.visit_status = 'BOOKED' AND ss.slot_start >= CURRENT_TIMESTAMP AND v.user_type = 'PUBLIC'",
    nativeQuery = true,
  )
  fun getPublicFutureBookingsByBookerReference(bookerReference: String): List<Visit>

  @Query(
    "SELECT v.* FROM visit v " +
      " INNER JOIN event_audit ea on ea.booking_reference = v.reference AND ea.type = 'BOOKED_VISIT' " +
      " INNER JOIN actioned_by ab on ab.id = ea.actioned_by_id" +
      " INNER JOIN session_slot ss on ss.id = v.session_slot_id " +
      " WHERE ab.booker_reference = :bookerReference " +
      " AND v.visit_status = 'BOOKED' AND ss.slot_start < CURRENT_TIMESTAMP AND v.user_type = 'PUBLIC'",
    nativeQuery = true,
  )
  fun getPublicPastBookingsByBookerReference(bookerReference: String): List<Visit>

  @Query(
    "Select v.* FROM visit v " +
      " INNER JOIN event_audit ea on ea.booking_reference = v.reference and ea.type = 'BOOKED_VISIT' " +
      " INNER JOIN actioned_by ab on ab.id = ea.actioned_by_id" +
      " WHERE ab.booker_reference = :bookerReference AND v.visit_status = 'CANCELLED' " +
      " AND v.user_type = 'PUBLIC'",
    nativeQuery = true,
  )
  fun getPublicCanceledVisitsByBookerReference(bookerReference: String): List<Visit>

  @Query(
    "SELECT v FROM Visit v WHERE v.reference in :bookingReferences order by v.modifyTimestamp",
  )
  fun findVisitsByReferences(bookingReferences: List<String>): List<Visit>
}
