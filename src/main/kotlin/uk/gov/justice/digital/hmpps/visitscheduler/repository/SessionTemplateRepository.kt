package uk.gov.justice.digital.hmpps.visitscheduler.repository

import org.springframework.data.jpa.repository.JpaRepository
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
      "and (:inclEnhancedPrivilegeTemplates is null or :inclEnhancedPrivilegeTemplates = true or u.enhanced = false)"
  )
  fun findValidSessionTemplatesBy(
    @Param("prisonCode") prisonCode: String,
    @Param("rangeStartDate") rangeStartDate: LocalDate? = null,
    @Param("rangeEndDate") rangeEndDate: LocalDate? = null,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek? = null,
    @Param("inclEnhancedPrivilegeTemplates") inclEnhancedPrivilegeTemplates: Boolean? = null
  ): List<SessionTemplate>

  @Query(
    "select u from SessionTemplate u " +
      "where u.prison.code = :prisonCode " +
      "and (u.validToDate is null or u.validToDate >= :sessionDate) " +
      "and (u.validFromDate <= :sessionDate) " +
      "and (u.startTime = :sessionStartTime) " +
      "and (u.endTime = :sessionEndTime) " +
      "and (u.dayOfWeek = :dayOfWeek)"
  )
  fun findValidSessionTemplatesForSession(
    @Param("prisonCode") prisonCode: String,
    @Param("sessionDate") sessionDate: LocalDate,
    @Param("sessionStartTime") sessionStartTime: LocalTime,
    @Param("sessionEndTime") sessionEndTime: LocalTime,
    @Param("dayOfWeek") dayOfWeek: DayOfWeek
  ): List<SessionTemplate>

  fun findByReference(sessionTemplateId: String): SessionTemplate?
}
