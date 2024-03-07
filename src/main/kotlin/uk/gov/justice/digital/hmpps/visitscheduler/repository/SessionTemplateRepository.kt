package uk.gov.justice.digital.hmpps.visitscheduler.repository

import jakarta.persistence.Tuple
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionCapacityDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionDateRangeDto
import uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.projections.VisitCountsByDate
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Repository
interface SessionTemplateRepository : JpaRepository<SessionTemplate, Long> {

  @Query(
    "SELECT MAX(open),MAX(closed) FROM" +
      "(SELECT  COUNT(CASE WHEN v.visit_restriction = 'OPEN' THEN 1 END) AS open, " +
      " COUNT(CASE WHEN v.visit_restriction = 'CLOSED' THEN 1 END) AS closed  FROM visit v " +
      " JOIN session_slot sl on sl.id = v.session_slot_id " +
      " WHERE sl.session_template_reference = :reference" +
      " AND sl.slot_start >= :visitsFromDate" +
      " AND (cast(:visitsToDate as date) is null OR sl.slot_end <= :visitsToDate)" +
      " AND visit_status = 'BOOKED'" +
      " GROUP BY sl.slot_date,sl.slot_start ) AS tmp ",
    nativeQuery = true,
  )
  fun findSessionTemplateMinCapacityBy(
    @Param("reference") reference: String,
    @Param("visitsFromDate") visitsFromDate: LocalDate,
    @Param("visitsToDate") visitsToDate: LocalDate?,
  ): Tuple

  @Query(
    "select sl.slot_date as visitDate, v.visit_restriction as visitRestriction, count(*) as visitCount from visit v " +
      " JOIN session_slot sl on sl.id = v.session_slot_id " +
      " WHERE sl.session_template_reference = :reference" +
      " AND sl.slot_date >= :visitsFromDate" +
      " AND (cast(:visitsToDate as date) is null OR sl.slot_date < (CAST(:visitsToDate AS DATE) + CAST('1 day' AS INTERVAL)))" +
      " AND visit_status = :visitStatus " +
      " GROUP BY sl.slot_date, v.visit_restriction" +
      " ORDER BY sl.slot_date",
    nativeQuery = true,
  )
  fun getVisitCountsByDate(
    @Param("reference") reference: String,
    @Param("visitsFromDate") visitsFromDate: LocalDate,
    @Param("visitsToDate") visitsToDate: LocalDate?,
    @Param("visitStatus") visitStatus: String,
  ): List<VisitCountsByDate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (cast(:rangeEndDate as date) is null or u.validFromDate <= :rangeEndDate) " +
      "and (cast(:rangeStartDate as date) is null or (u.validToDate is null or u.validToDate >= :rangeStartDate)) " +
      "and (:dayOfWeek is null or u.dayOfWeek = :dayOfWeek) " +
      "and (:visitRoom is null or u.visitRoom = :visitRoom)" +
      "and (u.active = true)",
  )
  fun findSessionTemplateMinCapacityBy(
    @Param("prisonCode") prisonCode: String,
    @Param("rangeStartDate") rangeStartDate: LocalDate? = null,
    @Param("rangeEndDate") rangeEndDate: LocalDate? = null,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek? = null,
    @Param("visitRoom") visitRoom: String? = null,
  ): List<SessionTemplate>

  @Query(
    "select st.visitRoom from SessionTemplate st " +
      "where st.reference = :reference ",
  )
  fun getVisitRoom(
    reference: String,
  ): String

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode order by u.validFromDate,u.validToDate",
  )
  fun findSessionTemplatesByPrisonCode(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (u.validToDate >= CURRENT_DATE or u.validToDate is null) order by u.validToDate",
  )
  fun findCurrentAndFutureSessionTemplates(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and u.validFromDate <= CURRENT_DATE and u.validToDate <= CURRENT_DATE order by u.validFromDate,u.validToDate",
  )
  fun findHistoricSessionTemplates(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (u.validToDate is null or u.validToDate >= :sessionDate) " +
      "and (u.validFromDate <= :sessionDate) " +
      "and (u.startTime = :sessionStartTime) " +
      "and (u.endTime = :sessionEndTime) " +
      "and (u.dayOfWeek = :dayOfWeek)" +
      "and (u.active = true)",
  )
  fun findValidSessionTemplatesForSession(
    @Param("prisonCode") prisonCode: String,
    @Param("sessionDate") sessionDate: LocalDate,
    @Param("sessionStartTime") sessionStartTime: LocalTime,
    @Param("sessionEndTime") sessionEndTime: LocalTime,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (u.validToDate is null or u.validToDate >= :sessionDate) " +
      "and (u.validFromDate <= :sessionDate) " +
      "and (u.dayOfWeek = :dayOfWeek)" +
      "and (u.active = true)",
  )
  fun findValidSessionTemplatesForSession(
    @Param("prisonCode") prisonCode: String,
    @Param("sessionDate") sessionDate: LocalDate,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek,
  ): List<SessionTemplate>

  fun findByReference(reference: String): SessionTemplate?

  @Modifying
  fun deleteByReference(reference: String): Int

  @Modifying(clearAutomatically = true)
  @Query("update SessionTemplate s set s.name = :name WHERE s.reference = :reference")
  fun updateNameByReference(reference: String, name: String): Int

  @Modifying(clearAutomatically = true)
  @Query("update SessionTemplate s set s.visitRoom = :visitRoom WHERE s.reference = :reference")
  fun updateVisitRoomByReference(reference: String, visitRoom: String): Int

  @Modifying(clearAutomatically = true)
  @Query("Update SessionTemplate s set s.startTime = :#{#sessionTimeSlot.startTime},  s.endTime = :#{#sessionTimeSlot.endTime} WHERE s.reference = :reference")
  fun updateSessionTimeSlotByReference(reference: String, sessionTimeSlot: SessionTimeSlotDto): Int

  @Modifying(clearAutomatically = true)
  @Query("Update SessionTemplate s set s.validFromDate = :#{#sessionDateRange.validFromDate},  s.validToDate = :#{#sessionDateRange.validToDate} WHERE s.reference = :reference")
  fun updateSessionDateRangeByReference(reference: String, sessionDateRange: SessionDateRangeDto): Int

  @Modifying(clearAutomatically = true)
  @Query("Update SessionTemplate s set s.closedCapacity = :#{#sessionCapacity.closed}, s.openCapacity = :#{#sessionCapacity.open} WHERE s.reference = :reference")
  fun updateCapacityByReference(reference: String, sessionCapacity: SessionCapacityDto): Int

  @Modifying(clearAutomatically = true)
  @Query("Update SessionTemplate s set s.weeklyFrequency = :weeklyFrequency WHERE s.reference = :reference")
  fun updateWeeklyFrequencyByReference(reference: String, weeklyFrequency: Int): Int

  @Modifying
  @Query("Update SessionTemplate s set s.active = :isActive WHERE s.reference = :reference")
  fun updateActiveByReference(reference: String, isActive: Boolean): Int

  @Modifying
  @Query("Update SessionTemplate s set s.includeLocationGroupType = :includeLocationGroupType WHERE s.reference = :reference")
  fun updateIncludeLocationGroupType(reference: String, includeLocationGroupType: Boolean): Int

  @Query(
    "SELECT new uk.gov.justice.digital.hmpps.visitscheduler.dto.sessions.SessionTimeSlotDto(st.startTime,st.endTime)  FROM SessionTemplate st WHERE st.reference = :reference",
  )
  fun getSessionTimeSlot(reference: String?): SessionTimeSlotDto?
}
