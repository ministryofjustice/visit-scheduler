package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.application.Application
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitRestrictionStats
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
interface ApplicationRepository :
  JpaRepository<Application, Long>,
  JpaSpecificationExecutor<Application> {

  @Query(
    "SELECT a FROM Application a " +
      "WHERE a.applicationStatus = 'IN_PROGRESS'" +
      " AND a.modifyTimestamp < :expiredDateAndTime ORDER BY a.id",
  )
  fun findApplicationByModifyTimes(expiredDateAndTime: LocalDateTime): List<Application>

  @Query(
    "SELECT a FROM Application a WHERE a.reference = :applicationReference",
  )
  fun findApplication(applicationReference: String): Application?

  @Modifying
  @Query(
    "Update application SET application_status = 'ACCEPTED' WHERE reference = :applicationReference",
    nativeQuery = true,
  )
  fun completeApplication(applicationReference: String): Int

  @Query(
    "SELECT count(a)>0 FROM Application a WHERE a.applicationStatus = 'ACCEPTED' AND a.reference = :applicationReference",
  )
  fun isApplicationCompleted(applicationReference: String): Boolean

  @Query(
    "SELECT a.restriction AS visitRestriction, COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction IN ('OPEN','CLOSED') AND " +
      " a.reserved_slot = true AND a.application_status = 'IN_PROGRESS' AND" +
      " a.modify_timestamp >= :expiredDateAndTime AND " +
      " (:excludedApplicationReference is null OR a.reference != :excludedApplicationReference ) " +
      " GROUP BY a.restriction",
    nativeQuery = true,
  )
  fun getCountOfReservedSessionForOpenOrClosedRestriction(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String?,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT a.restriction AS visitRestriction, COUNT(*) AS count FROM application a " +
      "inner join event_audit ea on a.reference = ea.application_reference " +
      "inner join actioned_by ab on ea.actioned_by_id = ab.id " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction IN ('OPEN','CLOSED') AND " +
      " a.reserved_slot = true AND a.application_status = 'IN_PROGRESS' AND" +
      " a.modify_timestamp >= :expiredDateAndTime AND " +
      " (:excludedApplicationReference is null OR a.reference != :excludedApplicationReference ) AND " +
      " (ea.type = 'RESERVED_VISIT' AND " +
      " (ab.user_name != :usernameToExcludeFromReservedApplications OR ab.booker_reference != :usernameToExcludeFromReservedApplications)) " +
      " GROUP BY a.restriction",
    nativeQuery = true,
  )
  fun getCountOfReservedSessionForOpenOrClosedRestrictionExcludingUser(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String?,
    @Param("usernameToExcludeFromReservedApplications") usernameToExcludeFromReservedApplications: String?,
  ): List<VisitRestrictionStats>

  @Query(
    "SELECT COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction = 'CLOSED' AND " +
      " a.reserved_slot = true AND a.application_status = 'IN_PROGRESS' AND" +
      " a.modify_timestamp >= :expiredDateAndTime AND " +
      " (:excludedApplicationReference is null OR a.reference != :excludedApplicationReference )",
    nativeQuery = true,
  )
  fun getCountOfReservedApplicationsForClosedSessionSlot(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String? = null,
  ): Long

  @Query(
    "SELECT COUNT(*) AS count  FROM application a " +
      "WHERE a.session_slot_id = :sessionSlotId AND " +
      " a.restriction = 'OPEN' AND " +
      " a.reserved_slot = true AND a.application_status = 'IN_PROGRESS' AND" +
      " a.modify_timestamp >= :expiredDateAndTime AND " +
      " (:excludedApplicationReference is null OR a.reference != :excludedApplicationReference )",
    nativeQuery = true,
  )
  fun getCountOfReservedApplicationsForOpenSessionSlot(
    sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String? = null,
  ): Long

  @Query(
    "SELECT COUNT(a) > 0 FROM Application a " +
      "WHERE a.applicationStatus = 'IN_PROGRESS' AND a.reservedSlot = true AND " +
      "a.prisonerId = :prisonerId AND " +
      "a.modifyTimestamp >= :expiredDateAndTime AND " +
      "a.sessionSlotId = :sessionSlotId AND " +
      "(:excludedApplicationReference is null OR a.reference != :excludedApplicationReference) ",
  )
  fun hasReservations(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionSlotId") sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String?,
  ): Boolean

  @Query(
    "SELECT COUNT(a) > 0 FROM Application a " +
      "inner join EventAudit ea on a.reference = ea.applicationReference " +
      "inner join ActionedBy ab on ea.actionedById = ab.id " +
      "WHERE a.applicationStatus = 'IN_PROGRESS' AND a.reservedSlot = true AND " +
      "a.prisonerId = :prisonerId AND " +
      "a.modifyTimestamp >= :expiredDateAndTime AND " +
      "a.sessionSlotId = :sessionSlotId AND " +
      "(:excludedApplicationReference is null OR a.reference != :excludedApplicationReference)  AND " +
      "(ea.type = 'RESERVED_VISIT' AND " +
      "(ab.userName != :usernameToExcludeFromReservedApplications OR ab.bookerReference != :usernameToExcludeFromReservedApplications))",
  )
  fun hasReservations(
    @Param("prisonerId") prisonerId: String,
    @Param("sessionSlotId") sessionSlotId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
    @Param("excludedApplicationReference") excludedApplicationReference: String?,
    @Param("usernameToExcludeFromReservedApplications") usernameToExcludeFromReservedApplications: String,
  ): Boolean

  @Query(
    "SELECT count(*) > 0 FROM application a left join session_slot sl on a.session_slot_id = sl.id " +
      "WHERE a.prisoner_id IN :prisonerIds AND " +
      "a.prison_id = :prisonId AND " +
      "sl.slot_date = :sessionDate AND " +
      "a.modify_timestamp >= :expiredDateAndTime AND " +
      "a.application_status = 'IN_PROGRESS' AND a.reserved_slot = true",
    nativeQuery = true,
  )
  fun hasActiveApplicationsForDate(
    prisonerIds: List<String>,
    sessionDate: LocalDate,
    prisonId: Long,
    @Param("expiredDateAndTime") expiredDateAndTime: LocalDateTime,
  ): Boolean
}
