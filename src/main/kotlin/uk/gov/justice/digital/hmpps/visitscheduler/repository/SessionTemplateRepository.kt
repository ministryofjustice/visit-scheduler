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
      "and (u.validToDate is null or u.validToDate >= :firstBookableDay) " +
      "and (u.validFromDate <= :lastBookableDay) " +
      "and (:inclEnhancedPrivilegeTemplates = true or u.enhanced = false)"
  )
  fun findValidSessionTemplatesByPrisonCode(
    @Param("prisonCode") prisonCode: String,
    @Param("firstBookableDay") firstBookableDay: LocalDate,
    @Param("lastBookableDay") lastBookableDay: LocalDate,
    @Param("inclEnhancedPrivilegeTemplates") inclEnhancedPrivilegeTemplates: Boolean
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
    @Param("dayOfWeek") dayOfWeek: DayOfWeek,

  ): List<SessionTemplate>
}
