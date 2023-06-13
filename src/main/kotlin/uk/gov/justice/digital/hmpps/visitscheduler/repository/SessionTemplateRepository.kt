package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.visitscheduler.model.entity.session.SessionTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Repository
interface SessionTemplateRepository : JpaRepository<SessionTemplate, Long> {

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (cast(:rangeEndDate as date) is null or u.validFromDate <= :rangeEndDate) " +
      "and (cast(:rangeStartDate as date) is null or (u.validToDate is null or u.validToDate >= :rangeStartDate)) " +
      "and (:dayOfWeek is null or u.dayOfWeek = :dayOfWeek) " +
      "and (:visitRoom is null or u.visitRoom = :visitRoom)",
  )
  fun findValidSessionTemplatesBy(
    @Param("prisonCode") prisonCode: String,
    @Param("rangeStartDate") rangeStartDate: LocalDate? = null,
    @Param("rangeEndDate") rangeEndDate: LocalDate? = null,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek? = null,
    @Param("visitRoom") visitRoom: String? = null,
  ): List<SessionTemplate>

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
  fun findActiveAndFutureSessionTemplates(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and u.validFromDate <= CURRENT_DATE and u.validToDate <= CURRENT_DATE order by u.validFromDate,u.validToDate",
  )
  fun findInActiveSessionTemplates(
    @Param("prisonCode") prisonCode: String,
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (u.validToDate is null or u.validToDate >= :sessionDate) " +
      "and (u.validFromDate <= :sessionDate) " +
      "and (u.startTime = :sessionStartTime) " +
      "and (u.endTime = :sessionEndTime) " +
      "and (u.dayOfWeek = :dayOfWeek)",
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
      "and (u.dayOfWeek = :dayOfWeek)",
  )
  fun findValidSessionTemplatesForSession(
    @Param("prisonCode") prisonCode: String,
    @Param("sessionDate") sessionDate: LocalDate,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek,
  ): List<SessionTemplate>

  fun findByReference(reference: String): SessionTemplate?

  @Modifying
  fun deleteByReference(reference: String): Int

  @Modifying
  @Query("update SessionTemplate s set s.name = :name WHERE s.reference = :reference")
  fun updateNameByReference(reference: String, name: String): Int

  @Modifying
  @Query("Update SessionTemplate s set s.startTime = :startTime WHERE s.reference = :reference")
  fun updateStartTimeByReference(reference: String, startTime: LocalTime): Int

  @Modifying
  @Query("Update SessionTemplate s set s.endTime = :endTime WHERE s.reference = :reference")
  fun updateEndTimeByReference(reference: String, endTime: LocalTime): Int

  @Modifying
  @Query("Update SessionTemplate s set s.validFromDate = :validFromDate WHERE s.reference = :reference")
  fun updateValidFromDateByReference(reference: String, validFromDate: LocalDate): Int

  @Modifying
  @Query("Update SessionTemplate s set s.validToDate = :validToDate WHERE s.reference = :reference")
  fun updateValidToDateByReference(reference: String, validToDate: LocalDate): Int

  @Modifying
  @Query("Update SessionTemplate s set s.closedCapacity = :closedCapacity WHERE s.reference = :reference")
  fun updateClosedCapacityByReference(reference: String, closedCapacity: Int): Int

  @Modifying
  @Query("Update SessionTemplate s set s.openCapacity = :openCapacity WHERE s.reference = :reference")
  fun updateOpenCapacityByReference(reference: String, openCapacity: Int): Int

  @Modifying
  @Query("Update SessionTemplate s set s.biWeekly = :biWeekly WHERE s.reference = :reference")
  fun updateBiWeeklyByReference(reference: String, biWeekly: Boolean): Int
}
